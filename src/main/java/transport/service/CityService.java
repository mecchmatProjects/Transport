package transport.service;

import java.util.Objects;
import transport.exception.ValidationException;
import transport.model.Bus;
import transport.model.City;
import transport.model.Route;
import transport.model.Tariff;
import transport.repository.Repository;
import transport.repository.TariffKey;

public class CityService extends AbstractCrudService<City, String> {

  private final Repository<Route, Integer> routes;
  private final Repository<Bus, String> buses;
  private final Repository<Tariff, TariffKey> tariffs;

  public CityService(
      Repository<City, String> cities,
      Repository<Route, Integer> routes,
      Repository<Bus, String> buses,
      Repository<Tariff, TariffKey> tariffs) {
    super(cities);
    this.routes = Objects.requireNonNull(routes, "routes");
    this.buses = Objects.requireNonNull(buses, "buses");
    this.tariffs = Objects.requireNonNull(tariffs, "tariffs");
  }

  @Override
  protected void validate(City city) {
    Objects.requireNonNull(city, "city");
  }

  @Override
  protected void beforeDelete(City city) {
    String id = city.id();
    routes.findAll(route -> route.indexOf(id) >= 0).stream().findFirst().ifPresent(route -> {
      throw new ValidationException("city " + id + " is used by route " + route.number());
    });
    buses.findAll(bus -> bus.getDepotCityId().equals(id)).stream().findFirst().ifPresent(bus -> {
      throw new ValidationException("city " + id + " is the depot of bus " + bus.getId());
    });
    if (!tariffs.findAll(t -> t.fromCityId().equals(id) || t.toCityId().equals(id)).isEmpty()) {
      throw new ValidationException("city " + id + " is used by tariffs");
    }
  }
}
