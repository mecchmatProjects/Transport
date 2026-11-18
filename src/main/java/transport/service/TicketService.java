package transport.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import transport.exception.NoSeatsAvailableException;
import transport.exception.ValidationException;
import transport.model.Bus;
import transport.model.Passenger;
import transport.model.Route;
import transport.model.Ticket;
import transport.model.Trip;
import transport.model.TripStatus;
import transport.repository.Repository;
import transport.service.tariff.TariffPolicy;

/** Продаж і скасування квитків з посегментним контролем місць. */
public class TicketService {

  private final Repository<Ticket, String> tickets;
  private final Repository<Trip, String> trips;
  private final Repository<Bus, String> buses;
  private final TariffPolicy pricing;
  private final Clock clock;

  public TicketService(
      Repository<Ticket, String> tickets,
      Repository<Trip, String> trips,
      Repository<Bus, String> buses,
      TariffPolicy pricing,
      Clock clock) {
    this.tickets = Objects.requireNonNull(tickets, "tickets");
    this.trips = Objects.requireNonNull(trips, "trips");
    this.buses = Objects.requireNonNull(buses, "buses");
    this.pricing = Objects.requireNonNull(pricing, "pricing");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public TicketService(
      Repository<Ticket, String> tickets,
      Repository<Trip, String> trips,
      Repository<Bus, String> buses,
      TariffPolicy pricing) {
    this(tickets, trips, buses, pricing, Clock.systemDefaultZone());
  }

  /** Продає квиток на перше місце, вільне на всіх ділянках поїздки. */
  public Ticket sell(String tripId, Passenger passenger, String fromCityId, String toCityId) {
    Leg leg = leg(tripId, fromCityId, toCityId);
    boolean[] taken = takenSeats(leg);
    OptionalInt seat = firstFree(taken);
    if (seat.isEmpty()) {
      throw new NoSeatsAvailableException(tripId, fromCityId, toCityId);
    }
    return issue(leg, passenger, seat.getAsInt());
  }

  /** Продає квиток на конкретне місце. */
  public Ticket sell(
      String tripId, Passenger passenger, String fromCityId, String toCityId, int seatNumber) {
    Leg leg = leg(tripId, fromCityId, toCityId);
    boolean[] taken = takenSeats(leg);
    if (seatNumber < 1 || seatNumber > leg.bus.getSeats()) {
      throw new ValidationException("bus " + leg.bus.getId() + " has no seat " + seatNumber);
    }
    if (taken[seatNumber]) {
      throw new ValidationException("seat " + seatNumber + " on trip " + tripId
          + " is taken for " + fromCityId + " -> " + toCityId);
    }
    return issue(leg, passenger, seatNumber);
  }

  /** Скасовує квиток; повторне скасування кидає {@code InvalidStatusTransitionException}. */
  public Ticket cancel(String ticketNumber) {
    Ticket ticket = get(ticketNumber);
    ticket.cancel();
    tickets.save(copyOf(ticket));
    return ticket;
  }

  /** Позначає квиток використаним (посадка відбулась). */
  public Ticket markUsed(String ticketNumber) {
    Ticket ticket = get(ticketNumber);
    ticket.markUsed();
    tickets.save(copyOf(ticket));
    return ticket;
  }

  /** Скасовує всі активні квитки рейсу; повертає їх кількість. */
  public int cancelAllForTrip(String tripId) {
    List<Ticket> active = tickets.findAll(t -> t.getTripId().equals(tripId) && t.isActive());
    active.forEach(t -> cancel(t.getNumber()));
    return active.size();
  }

  /** Кількість місць, вільних на всьому відрізку {@code from -> to}. */
  public int availableSeats(String tripId, String fromCityId, String toCityId) {
    boolean[] taken = takenSeats(leg(tripId, fromCityId, toCityId));
    int free = 0;
    for (int seat = 1; seat < taken.length; seat++) {
      if (!taken[seat]) {
        free++;
      }
    }
    return free;
  }

  /** Кількість пасажирів на кожній ділянці рейсу, індекс = номер ділянки. */
  public List<Integer> passengersBySegment(String tripId) {
    Trip trip = trips.getById(tripId);
    Route route = trip.getRoute();
    int[] load = new int[route.segments().size()];
    for (Ticket t : activeTickets(tripId)) {
      // Квиток з містами поза маршрутом не можна віднести до ділянок, тож пропускаємо
      rangeOf(route, t).ifPresent(range -> {
        for (int s = range[0]; s < range[1]; s++) {
          load[s]++;
        }
      });
    }
    List<Integer> result = new ArrayList<>(load.length);
    for (int value : load) {
      result.add(value);
    }
    return List.copyOf(result);
  }

  /** Копія квитка або {@code EntityNotFoundException}. */
  public Ticket get(String ticketNumber) {
    return copyOf(tickets.getById(ticketNumber));
  }

  /** Усі квитки рейсу за номером місця. */
  public List<Ticket> findByTrip(String tripId) {
    return tickets.findAll(t -> t.getTripId().equals(tripId)).stream()
        .sorted(Comparator.comparingInt(Ticket::getSeatNumber).thenComparing(Ticket::getNumber))
        .map(TicketService::copyOf)
        .toList();
  }

  /** Усі квитки в порядку продажу. */
  public List<Ticket> list() {
    return tickets.findAll().stream().map(TicketService::copyOf).toList();
  }

  private Leg leg(String tripId, String fromCityId, String toCityId) {
    Objects.requireNonNull(fromCityId, "fromCityId");
    Objects.requireNonNull(toCityId, "toCityId");
    Trip trip = trips.getById(tripId);
    if (trip.getStatus() != TripStatus.SCHEDULED) {
      throw new ValidationException("trip " + tripId + " is " + trip.getStatus()
          + ", tickets are sold only for SCHEDULED trips");
    }
    if (!trip.hasBus()) {
      throw new ValidationException("trip " + tripId + " has no bus assigned");
    }
    Route route = trip.getRoute();
    // Ловить і from == to, і зворотний напрям
    if (!route.servesDirection(fromCityId, toCityId)) {
      throw new ValidationException(
          "trip " + tripId + " does not go " + fromCityId + " -> " + toCityId);
    }
    int[] range = route.stopRange(fromCityId, toCityId);
    return new Leg(trip, buses.getById(trip.getBusId()), fromCityId, toCityId, range[0], range[1]);
  }

  // taken[seat]: місце зайняте хоча б на одній ділянці відрізка; індекс 0 порожній
  private boolean[] takenSeats(Leg leg) {
    boolean[] taken = new boolean[leg.bus.getSeats() + 1];
    Route route = leg.trip.getRoute();
    for (Ticket t : activeTickets(leg.trip.getId())) {
      // Битий квиток займає місце на всьому рейсі, щоб не продати його двічі
      boolean overlaps = rangeOf(route, t)
          .map(range -> range[0] < leg.toIndex && leg.fromIndex < range[1])
          .orElse(true);
      int seat = t.getSeatNumber();
      if (overlaps && seat < taken.length) {
        taken[seat] = true;
      }
    }
    return taken;
  }

  // Порожньо, якщо міста квитка (зіпсовані дані з JSON) не на маршруті
  private static Optional<int[]> rangeOf(Route route, Ticket t) {
    if (!route.servesDirection(t.getFromCityId(), t.getToCityId())) {
      return Optional.empty();
    }
    return Optional.of(route.stopRange(t.getFromCityId(), t.getToCityId()));
  }

  private static OptionalInt firstFree(boolean[] taken) {
    for (int seat = 1; seat < taken.length; seat++) {
      if (!taken[seat]) {
        return OptionalInt.of(seat);
      }
    }
    return OptionalInt.empty();
  }

  private Ticket issue(Leg leg, Passenger passenger, int seat) {
    Objects.requireNonNull(passenger, "passenger");
    BigDecimal price = pricing.requireTicketPrice(leg.trip.getRoute(), leg.from, leg.to);
    Ticket ticket = new Ticket(nextNumber(leg.trip.getId()), leg.trip.getId(), passenger,
        leg.from, leg.to, seat, price, LocalDateTime.now(clock));
    tickets.add(copyOf(ticket));
    return ticket;
  }

  private List<Ticket> activeTickets(String tripId) {
    return tickets.findAll(t -> t.getTripId().equals(tripId) && t.isActive());
  }

  // Номер виду T1-001; скасовані квитки лишаються в сховищі, тому номер не повторюється
  private String nextNumber(String tripId) {
    long n = tickets.findAll(t -> t.getTripId().equals(tripId)).size() + 1;
    String number = String.format("%s-%03d", tripId, n);
    while (tickets.existsById(number)) {
      number = String.format("%s-%03d", tripId, ++n);
    }
    return number;
  }

  static Ticket copyOf(Ticket t) {
    return new Ticket(t.getNumber(), t.getTripId(), t.getPassenger(), t.getFromCityId(),
        t.getToCityId(), t.getSeatNumber(), t.getPrice(), t.getSoldAt(), t.getStatus());
  }

  private record Leg(Trip trip, Bus bus, String from, String to, int fromIndex, int toIndex) {}
}
