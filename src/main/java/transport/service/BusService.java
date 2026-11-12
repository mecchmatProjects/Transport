package transport.service;

import java.util.List;
import java.util.Objects;
import transport.exception.ValidationException;
import transport.model.Bus;
import transport.model.City;
import transport.model.TimeWindow;
import transport.model.Trip;
import transport.model.TripStatus;
import transport.repository.Repository;

public class BusService extends AbstractCrudService<Bus, String> {

  private final Repository<City, String> cities;
  private final Repository<Trip, String> trips;

  public BusService(
      Repository<Bus, String> buses,
      Repository<City, String> cities,
      Repository<Trip, String> trips) {
    super(buses);
    this.cities = Objects.requireNonNull(cities, "cities");
    this.trips = Objects.requireNonNull(trips, "trips");
  }

  /** Автобуси, справні й вільні від ремонту та інших активних рейсів у цьому вікні. */
  public List<Bus> available(TimeWindow window) {
    Objects.requireNonNull(window, "window");
    return list().stream()
        .filter(bus -> bus.isAvailableFor(window))
        .filter(bus -> activeTripsOf(bus.getId()).stream()
            .noneMatch(trip -> trip.timeWindow().overlaps(window)))
        .toList();
  }

  /** Рейси автобуса, які ще не завершені й не скасовані. */
  public List<Trip> activeTripsOf(String busId) {
    return trips.findAll(trip -> busId.equals(trip.getBusId()) && isActive(trip));
  }

  @Override
  protected void validate(Bus bus) {
    Objects.requireNonNull(bus, "bus");
    if (!cities.existsById(bus.getDepotCityId())) {
      throw new ValidationException(
          "bus " + bus.getId() + ": unknown depot city " + bus.getDepotCityId());
    }
  }

  // Завершені й скасовані рейси теж тримають busId, на них спираються звіти
  @Override
  protected void beforeDelete(Bus bus) {
    String id = bus.getId();
    trips.findAll(trip -> id.equals(trip.getBusId())).stream().findFirst().ifPresent(trip -> {
      throw new ValidationException("bus " + id + " is referenced by trip " + trip.getId());
    });
  }

  static boolean isActive(Trip trip) {
    return trip.getStatus() == TripStatus.SCHEDULED || trip.getStatus() == TripStatus.IN_PROGRESS;
  }
}
