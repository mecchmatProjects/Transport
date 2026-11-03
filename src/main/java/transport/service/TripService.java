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
    trip.assignBus(busId);
    return repository.save(trip);
  }

  /** Знімає автобус із запланованого рейсу. */
  public Trip unassignBus(String tripId) {
    Trip trip = get(tripId);
    requireScheduled(trip);
    trip.unassignBus();
    return repository.save(trip);
  }

  /** Скасовує рейс; квитки й багаж скасовують K3/K4. */
  public Trip cancel(String tripId) {
    Trip trip = get(tripId);
    trip.changeStatus(TripStatus.CANCELLED);
    return repository.save(trip);
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

  @Override
  protected void beforeUpdate(Trip existing, Trip updated) {
    if (updated.hasBus() && BusService.isActive(updated)) {
      checkBusFits(updated, updated.getBusId());
    }
  }

  @Override
  protected void beforeDelete(Trip trip) {
    String id = trip.getId();
    if (!tickets.findAll(t -> t.getTripId().equals(id) && t.isActive()).isEmpty()) {
      throw new ValidationException("trip " + id + " has active tickets");
    }
    if (!luggage.findAll(l -> l.getTripId().equals(id) && l.occupiesCapacity()).isEmpty()) {
      throw new ValidationException("trip " + id + " has luggage on board");
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

  private static void requireScheduled(Trip trip) {
    if (trip.getStatus() != TripStatus.SCHEDULED) {
      throw new ValidationException(
          "trip " + trip.getId() + " is " + trip.getStatus() + ", expected SCHEDULED");
    }
  }

  private static List<Trip> sorted(List<Trip> trips) {
    return trips.stream().sorted(BY_DEPARTURE).toList();
  }
}
