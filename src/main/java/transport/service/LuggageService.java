package transport.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import transport.exception.LuggageCapacityExceededException;
import transport.exception.ValidationException;
import transport.model.Bus;
import transport.model.Contact;
import transport.model.Luggage;
import transport.model.LuggageStatus;
import transport.model.PassengerLuggage;
import transport.model.Route;
import transport.model.Shipment;
import transport.model.Ticket;
import transport.model.Trip;
import transport.model.TripStatus;
import transport.repository.Repository;
import transport.service.tariff.TariffPolicy;

/** Облік багажу: реєстрація з посегментним контролем ваги і життєвий цикл статусів. */
public class LuggageService {

  // Похибка порівняння ваги: кілограми з дробами не мають давати хибну відмову
  private static final double EPS = 1e-9;

  private final Repository<Luggage, String> luggage;
  private final Repository<Ticket, String> tickets;
  private final Repository<Trip, String> trips;
  private final Repository<Bus, String> buses;
  private final TariffPolicy pricing;

  public LuggageService(
      Repository<Luggage, String> luggage,
      Repository<Ticket, String> tickets,
      Repository<Trip, String> trips,
      Repository<Bus, String> buses,
      TariffPolicy pricing) {
    this.luggage = Objects.requireNonNull(luggage, "luggage");
    this.tickets = Objects.requireNonNull(tickets, "tickets");
    this.trips = Objects.requireNonNull(trips, "trips");
    this.buses = Objects.requireNonNull(buses, "buses");
    this.pricing = Objects.requireNonNull(pricing, "pricing");
  }

  /** Реєструє багаж пасажира на відрізку його квитка. */
  public PassengerLuggage registerPassengerLuggage(
      String ticketNumber, double weightKg, double volumeM3) {
    requireMeasures(weightKg, volumeM3);
    Ticket ticket = tickets.getById(ticketNumber);
    if (!ticket.isActive()) {
      throw new ValidationException("ticket " + ticketNumber + " is " + ticket.getStatus()
          + ", luggage needs a SOLD ticket");
    }
    Leg leg = leg(ticket.getTripId(), ticket.getFromCityId(), ticket.getToCityId());
    checkCapacity(leg, weightKg);
    BigDecimal price = pricing.requireLuggagePrice(leg.route(), leg.from, leg.to, weightKg);
    PassengerLuggage item =
        new PassengerLuggage(nextId(leg.trip.getId(), "L"), ticket, weightKg, volumeM3, price);
    luggage.add(copyOf(item));
    return item;
  }

  /** Реєструє окреме відправлення без пасажира. */
  public Shipment registerShipment(
      String tripId,
      String fromCityId,
      String toCityId,
      double weightKg,
      double volumeM3,
      Contact sender,
      Contact recipient) {
    Objects.requireNonNull(sender, "sender");
    Objects.requireNonNull(recipient, "recipient");
    requireMeasures(weightKg, volumeM3);
    Leg leg = leg(tripId, fromCityId, toCityId);
    checkCapacity(leg, weightKg);
    BigDecimal price = pricing.requireLuggagePrice(leg.route(), leg.from, leg.to, weightKg);
    Shipment item = new Shipment(nextId(tripId, "S"), tripId, fromCityId, toCityId, weightKg,
        volumeM3, price, sender, recipient);
    luggage.add(copyOf(item));
    return item;
  }

  public Luggage load(String id) {
    return transition(id, Luggage::load);
  }

  public Luggage depart(String id) {
    return transition(id, Luggage::depart);
  }

  public Luggage arrive(String id) {
    return transition(id, Luggage::arrive);
  }

  public Luggage deliver(String id) {
    return transition(id, Luggage::deliver);
  }

  /** Скасовує багаж; повторне скасування кидає {@code InvalidStatusTransitionException}. */
  public Luggage cancel(String id) {
    return transition(id, Luggage::cancel);
  }

  /** Скасовує весь багаж рейсу, який ще можна скасувати; повертає його кількість. */
  public int cancelAllForTrip(String tripId) {
    List<Luggage> cancellable = luggage.findAll(l -> l.getTripId().equals(tripId)
        && l.getStatus().canTransitionTo(LuggageStatus.CANCELLED));
    cancellable.forEach(l -> cancel(l.getId()));
    return cancellable.size();
  }

  /** Скасовує багаж квитка, який ще можна скасувати, і повертає його кількість. */
  public int cancelForTicket(String ticketNumber) {
    Objects.requireNonNull(ticketNumber, "ticketNumber");
    List<Luggage> cancellable = luggage.findAll(l -> l instanceof PassengerLuggage p
        && p.getTicketNumber().equals(ticketNumber)
        && l.getStatus().canTransitionTo(LuggageStatus.CANCELLED));
    cancellable.forEach(l -> cancel(l.getId()));
    return cancellable.size();
  }

  /** Скільки кілограмів ще влізе на всьому відрізку {@code from -> to}. */
  public double availableKg(String tripId, String fromCityId, String toCityId) {
    Leg leg = leg(tripId, fromCityId, toCityId);
    double[] load = load(leg.trip, true);
    double capacity = leg.bus.getLuggageCapacityKg();
    double free = capacity;
    for (int s = leg.fromIndex; s < leg.toIndex; s++) {
      free = Math.min(free, capacity - load[s]);
    }
    return Math.max(0, free);
  }

  /** Вага багажу на кожній ділянці рейсу, індекс = номер ділянки. */
  public List<Double> loadBySegment(String tripId) {
    double[] load = load(trips.getById(tripId), false);
    List<Double> result = new ArrayList<>(load.length);
    for (double kg : load) {
      result.add(kg);
    }
    return List.copyOf(result);
  }

  /** Копія багажу або {@code EntityNotFoundException}. */
  public Luggage get(String id) {
    return copyOf(luggage.getById(id));
  }

  /** Увесь багаж рейсу за номером. */
  public List<Luggage> findByTrip(String tripId) {
    return luggage.findAll(l -> l.getTripId().equals(tripId)).stream()
        .sorted(Comparator.comparing(Luggage::getId))
        .map(LuggageService::copyOf)
        .toList();
  }

  /** Увесь багаж у порядку реєстрації. */
  public List<Luggage> list() {
    return luggage.findAll().stream().map(LuggageService::copyOf).toList();
  }

  private Luggage transition(String id, Consumer<Luggage> action) {
    Luggage item = get(id);
    action.accept(item);
    luggage.save(copyOf(item));
    return item;
  }

  private static void requireMeasures(double weightKg, double volumeM3) {
    if (!Double.isFinite(weightKg) || weightKg <= 0) {
      throw new ValidationException("luggage weight must be a positive number, got " + weightKg);
    }
    if (!Double.isFinite(volumeM3) || volumeM3 < 0) {
      throw new ValidationException("luggage volume must not be negative, got " + volumeM3);
    }
  }

  private Leg leg(String tripId, String fromCityId, String toCityId) {
    Objects.requireNonNull(fromCityId, "fromCityId");
    Objects.requireNonNull(toCityId, "toCityId");
    Trip trip = trips.getById(tripId);
    if (trip.getStatus() != TripStatus.SCHEDULED) {
      throw new ValidationException("trip " + tripId + " is " + trip.getStatus()
          + ", luggage is registered only for SCHEDULED trips");
    }
    if (!trip.hasBus()) {
      throw new ValidationException("trip " + tripId + " has no bus assigned");
    }
    if (!trip.getRoute().servesDirection(fromCityId, toCityId)) {
      throw new ValidationException(
          "trip " + tripId + " does not go " + fromCityId + " -> " + toCityId);
    }
    int[] range = trip.getRoute().stopRange(fromCityId, toCityId);
    return new Leg(trip, buses.getById(trip.getBusId()), fromCityId, toCityId, range[0], range[1]);
  }

  // Перевіряє кожну ділянку відрізка, бо автобус може бути повний лише на одній з них
  private void checkCapacity(Leg leg, double weightKg) {
    double[] load = load(leg.trip, true);
    double capacity = leg.bus.getLuggageCapacityKg();
    for (int s = leg.fromIndex; s < leg.toIndex; s++) {
      double free = capacity - load[s];
      if (weightKg > free + EPS) {
        throw new LuggageCapacityExceededException(
            leg.trip.getId(), s, weightKg, Math.max(0, free));
      }
    }
  }

  // Запис з містами поза маршрутом (зіпсований JSON) або займає весь рейс, або пропускається
  private double[] load(Trip trip, boolean brokenFillsTrip) {
    Route route = trip.getRoute();
    double[] load = new double[route.segments().size()];
    for (Luggage item : luggage.findAll(l -> l.getTripId().equals(trip.getId())
        && l.occupiesCapacity())) {
      Optional<int[]> range = rangeOf(route, item);
      if (range.isEmpty() && !brokenFillsTrip) {
        continue;
      }
      int from = range.map(r -> r[0]).orElse(0);
      int to = range.map(r -> r[1]).orElse(load.length);
      for (int s = from; s < to; s++) {
        load[s] += item.getWeightKg();
      }
    }
    return load;
  }

  private static Optional<int[]> rangeOf(Route route, Luggage item) {
    if (!route.servesDirection(item.getFromCityId(), item.getToCityId())) {
      return Optional.empty();
    }
    return Optional.of(route.stopRange(item.getFromCityId(), item.getToCityId()));
  }

  // T1-L001 для багажу пасажира, T1-S001 для відправлення; скасований лишається в сховищі
  private String nextId(String tripId, String kind) {
    String prefix = tripId + "-" + kind;
    long n = luggage.findAll(l -> l.getId().startsWith(prefix)).size() + 1;
    String id = String.format("%s%03d", prefix, n);
    while (luggage.existsById(id)) {
      id = String.format("%s%03d", prefix, ++n);
    }
    return id;
  }

  static Luggage copyOf(Luggage item) {
    return switch (item) {
      case PassengerLuggage p -> new PassengerLuggage(p.getId(), p.getTripId(),
          p.getFromCityId(), p.getToCityId(), p.getWeightKg(), p.getVolumeM3(), p.getPrice(),
          p.getStatus(), p.getTicketNumber());
      case Shipment s -> new Shipment(s.getId(), s.getTripId(), s.getFromCityId(),
          s.getToCityId(), s.getWeightKg(), s.getVolumeM3(), s.getPrice(), s.getStatus(),
          s.getSender(), s.getRecipient());
    };
  }

  private record Leg(Trip trip, Bus bus, String from, String to, int fromIndex, int toIndex) {

    Route route() {
      return trip.getRoute();
    }
  }
}
