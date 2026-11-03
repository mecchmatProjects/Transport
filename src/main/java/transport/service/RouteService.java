package transport.service;

import java.util.List;
import java.util.Objects;
import transport.exception.ValidationException;
import transport.model.City;
import transport.model.Route;
import transport.model.ScheduleTemplate;
import transport.model.Tariff;
import transport.model.Trip;
import transport.repository.Repository;
import transport.repository.TariffKey;

public class RouteService extends AbstractCrudService<Route, Integer> {

  public static final int DEFAULT_MAX_ROUTES = 30;

  private final Repository<City, String> cities;
  private final Repository<Trip, String> trips;
  private final Repository<ScheduleTemplate, String> templates;
  private final Repository<Tariff, TariffKey> tariffs;
  private final int maxRoutes;

  public RouteService(
      Repository<Route, Integer> routes,
      Repository<City, String> cities,
      Repository<Trip, String> trips,
      Repository<ScheduleTemplate, String> templates,
      Repository<Tariff, TariffKey> tariffs) {
    this(routes, cities, trips, templates, tariffs, DEFAULT_MAX_ROUTES);
  }

  public RouteService(
      Repository<Route, Integer> routes,
      Repository<City, String> cities,
      Repository<Trip, String> trips,
      Repository<ScheduleTemplate, String> templates,
      Repository<Tariff, TariffKey> tariffs,
      int maxRoutes) {
    super(routes);
    this.cities = Objects.requireNonNull(cities, "cities");
    this.trips = Objects.requireNonNull(trips, "trips");
    this.templates = Objects.requireNonNull(templates, "templates");
    this.tariffs = Objects.requireNonNull(tariffs, "tariffs");
    if (maxRoutes <= 0) {
      throw new IllegalArgumentException("maxRoutes must be > 0, got " + maxRoutes);
    }
    this.maxRoutes = maxRoutes;
  }

  public int maxRoutes() {
    return maxRoutes;
  }

  /** Маршрути, що проходять через {@code from}, а потім через {@code to}. */
  public List<Route> findRoutesBetween(String fromCityId, String toCityId) {
    return repository.findAll(route -> route.servesDirection(fromCityId, toCityId));
  }

  @Override
  protected void validate(Route route) {
    Objects.requireNonNull(route, "route");
    for (City stop : route.stops()) {
      if (!cities.existsById(stop.id())) {
        throw new ValidationException(
            "route " + route.number() + ": unknown city " + stop.id());
      }
    }
  }

  @Override
  protected void beforeCreate(Route route) {
    if (repository.count() >= maxRoutes) {
      throw new ValidationException("route limit reached: " + maxRoutes);
    }
  }

  // Рейси тримають об'єкт маршруту, тож зміна маршруту з рейсами переписала б історію
  @Override
  protected void beforeUpdate(Route existing, Route updated) {
    requireNoTrips(existing, "change");
  }

  @Override
  protected void beforeDelete(Route route) {
    requireNoTrips(route, "delete");
    int number = route.number();
    templates.findAll(t -> t.routeNumber() == number).stream().findFirst().ifPresent(t -> {
      throw new ValidationException("route " + number + " is used by template " + t.id());
    });
    if (!tariffs.findAll(t -> t.routeNumber() != null && t.routeNumber() == number).isEmpty()) {
      throw new ValidationException("route " + number + " has route-specific tariffs");
    }
  }

  private void requireNoTrips(Route route, String action) {
    int number = route.number();
    if (!trips.findAll(trip -> trip.getRoute().number() == number).isEmpty()) {
      throw new ValidationException("cannot " + action + " route " + number + ": it has trips");
    }
  }
}
