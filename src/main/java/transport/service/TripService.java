package transport.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import transport.exception.ScheduleConflictException;
import transport.exception.ValidationException;
import transport.model.Bus;
import transport.model.Luggage;
import transport.model.Route;
import transport.model.Ticket;
import transport.model.Trip;
import transport.model.TripStatus;
import transport.repository.Repository;

public class TripService extends AbstractCrudService<Trip, String> {

  private static final Comparator<Trip> BY_DEPARTURE =
      Comparator.comparing(Trip::departure).thenComparing(Trip::getId);

  // Така сама похибка ваги, як у LuggageService
  private static final double LUGGAGE_EPS = 1e-9;

  private final Repository<Route, Integer> routes;
  private final Repository<Bus, String> buses;
  private final Repository<Ticket, String> tickets;
  private final Repository<Luggage, String> luggage;

  public TripService(
      Repository<Trip, String> trips,
      Repository<Route, Integer> routes,
      Repository<Bus, String> buses,
      Repository<Ticket, String> tickets,
      Repository<Luggage, String> luggage) {
    super(trips);
    this.routes = Objects.requireNonNull(routes, "routes");
    this.buses = Objects.requireNonNull(buses, "buses");
    this.tickets = Objects.requireNonNull(tickets, "tickets");
    this.luggage = Objects.requireNonNull(luggage, "luggage");
  }

  /** Призначає автобус; кидає {@code ScheduleConflictException} при перетині з його рейсами. */
  public Trip assignBus(String tripId, String busId) {
    Trip trip = get(tripId);
    requireScheduled(trip);
    checkBusFits(trip, busId);
    checkBusHolds(trip, buses.getById(busId));
    trip.assignBus(busId);
    return store(trip);
  }

  /** Знімає автобус із запланованого рейсу. */
  public Trip unassignBus(String tripId) {
    Trip trip = get(tripId);
    requireScheduled(trip);
    trip.unassignBus();
    return store(trip);
  }

  /** Скасовує рейс; квитки й багаж скасовують K3/K4. */
  public Trip cancel(String tripId) {
    Trip trip = get(tripId);
    trip.changeStatus(TripStatus.CANCELLED);
    return store(trip);
  }

  /** Рейси за датою відправлення, впорядковані за часом. */
  public List<Trip> findByDate(LocalDate date) {
    Objects.requireNonNull(date, "date");
    return sorted(repository.findAll(trip -> trip.getDate().equals(date)));
  }

  /** Рейси автобуса, впорядковані за часом. */
  public List<Trip> findByBus(String busId) {
    Objects.requireNonNull(busId, "busId");
    return sorted(repository.findAll(trip -> busId.equals(trip.getBusId())));
  }

  /** Рейси маршруту, впорядковані за часом. */
  public List<Trip> findByRoute(int routeNumber) {
    return sorted(repository.findAll(trip -> trip.getRoute().number() == routeNumber));
  }

  @Override
  protected void validate(Trip trip) {
    Objects.requireNonNull(trip, "trip");
    Route route = trip.getRoute();
    Route stored = routes.findById(route.number()).orElseThrow(
        () -> new ValidationException(
            "trip " + trip.getId() + ": unknown route " + route.number()));
    if (!stored.equals(route)) {
      throw new ValidationException(
          "trip " + trip.getId() + ": route " + route.number() + " differs from the catalog");
    }
  }

  @Override
  protected void beforeCreate(Trip trip) {
    if (trip.hasBus() && BusService.isActive(trip)) {
      checkBusFits(trip, trip.getBusId());
    }
  }

  // Статус, маршрут і автобус змінюються лише командами; update переносить тільки час
  @Override
  protected void beforeUpdate(Trip existing, Trip updated) {
    String id = existing.getId();
    requireSame(id, "status", existing.getStatus(), updated.getStatus(), "cancel");
    requireSame(id, "route", existing.getRoute().number(), updated.getRoute().number(), "create");
    requireSame(id, "bus", existing.getBusId(), updated.getBusId(), "assignBus/unassignBus");
    if (!existing.timeWindow().equals(updated.timeWindow())) {
      requireScheduled(existing);
      if (updated.hasBus()) {
        checkBusFits(updated, updated.getBusId());
      }
    }
  }

  @Override
  protected void beforeDelete(Trip trip) {
    String id = trip.getId();
    if (!tickets.findAll(t -> t.getTripId().equals(id)).isEmpty()) {
      throw new ValidationException("trip " + id + " has tickets");
    }
    if (!luggage.findAll(l -> l.getTripId().equals(id)).isEmpty()) {
      throw new ValidationException("trip " + id + " has luggage");
    }
  }

  private void checkBusFits(Trip trip, String busId) {
    Bus bus = buses.getById(busId);
    if (!bus.isAvailableFor(trip.timeWindow())) {
      throw new ValidationException("bus " + busId + " is not available for trip "
          + trip.getId() + " (" + bus.getStatus() + ")");
    }
    // Розворот і місто старту перевіряє ConflictChecker; тут лише пряме перекриття
    repository.findAll(other -> busId.equals(other.getBusId()) && BusService.isActive(other))
        .stream()
        .filter(other -> trip.conflictsWith(other, Duration.ZERO))
        .findFirst()
        .ifPresent(other -> {
          throw new ScheduleConflictException("bus " + busId + ": trip " + trip.getId()
              + " overlaps trip " + other.getId());
        });
  }

  // Автобус має вмістити вже продані квитки й багаж на кожній ділянці рейсу
  private void checkBusHolds(Trip trip, Bus bus) {
    Route route = trip.getRoute();
    int segments = route.segments().size();
    int[] passengers = new int[segments];
    double[] kg = new double[segments];
    int maxSeat = 0;
    for (Ticket t : tickets.findAll(t -> t.getTripId().equals(trip.getId()) && t.isActive())) {
      maxSeat = Math.max(maxSeat, t.getSeatNumber());
      int[] range = rangeOf(route, t.getFromCityId(), t.getToCityId());
      for (int s = range[0]; s < range[1]; s++) {
        passengers[s]++;
      }
    }
    for (Luggage l : luggage.findAll(l -> l.getTripId().equals(trip.getId())
        && l.occupiesCapacity())) {
      int[] range = rangeOf(route, l.getFromCityId(), l.getToCityId());
      for (int s = range[0]; s < range[1]; s++) {
        kg[s] += l.getWeightKg();
      }
    }
    for (int s = 0; s < segments; s++) {
      if (passengers[s] > bus.getSeats()) {
        throw new ValidationException("bus " + bus.getId() + " has " + bus.getSeats()
            + " seats, trip " + trip.getId() + " has " + passengers[s] + " passengers on segment "
            + s);
      }
      if (kg[s] > bus.getLuggageCapacityKg() + LUGGAGE_EPS) {
        throw new ValidationException("bus " + bus.getId() + " holds "
            + bus.getLuggageCapacityKg() + " kg of luggage, trip " + trip.getId() + " has "
            + kg[s] + " kg on segment " + s);
      }
    }
    if (maxSeat > bus.getSeats()) {
      throw new ValidationException("bus " + bus.getId() + " has " + bus.getSeats()
          + " seats, trip " + trip.getId() + " has a ticket for seat " + maxSeat);
    }
  }

  // Запис з містами поза маршрутом (зіпсований JSON) займає весь рейс, як і в сервісах продажу
  private static int[] rangeOf(Route route, String fromCityId, String toCityId) {
    return route.servesDirection(fromCityId, toCityId)
        ? route.stopRange(fromCityId, toCityId)
        : new int[] {0, route.segments().size()};
  }

  private static void requireScheduled(Trip trip) {
    if (trip.getStatus() != TripStatus.SCHEDULED) {
      throw new ValidationException(
          "trip " + trip.getId() + " is " + trip.getStatus() + ", expected SCHEDULED");
    }
  }

  private static void requireSame(
      String id, String field, Object before, Object after, String command) {
    if (!Objects.equals(before, after)) {
      throw new ValidationException("trip " + id + ": " + field + " cannot be changed by update,"
          + " use " + command);
    }
  }

  @Override
  protected Trip copy(Trip trip) {
    return copyOf(trip);
  }

  static Trip copyOf(Trip trip) {
    return new Trip(trip.getId(), trip.getRoute(), trip.getDate(), trip.getDepartureTime(),
        trip.getBusId(), trip.getStatus());
  }

  private static List<Trip> sorted(List<Trip> trips) {
    return trips.stream().sorted(BY_DEPARTURE).map(TripService::copyOf).toList();
  }
}
