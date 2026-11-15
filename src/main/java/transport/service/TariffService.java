package transport.service;

import java.util.Objects;
import java.util.Optional;
import transport.exception.ValidationException;
import transport.model.City;
import transport.model.Route;
import transport.model.Tariff;
import transport.repository.Repository;
import transport.repository.TariffKey;

public class TariffService extends AbstractCrudService<Tariff, TariffKey> {

  private final Repository<City, String> cities;
  private final Repository<Route, Integer> routes;

  public TariffService(
      Repository<Tariff, TariffKey> tariffs,
      Repository<City, String> cities,
      Repository<Route, Integer> routes) {
    super(tariffs);
    this.cities = Objects.requireNonNull(cities, "cities");
    this.routes = Objects.requireNonNull(routes, "routes");
  }

  /** Тариф маршруту, а якщо його немає — загальний тариф на напрям. */
  public Optional<Tariff> find(int routeNumber, String fromCityId, String toCityId) {
    return repository.findById(new TariffKey(fromCityId, toCityId, routeNumber))
        .or(() -> repository.findById(new TariffKey(fromCityId, toCityId, null)));
  }

  @Override
  protected void validate(Tariff tariff) {
    Objects.requireNonNull(tariff, "tariff");
    requireCity(tariff.fromCityId());
    requireCity(tariff.toCityId());
    if (tariff.isRouteSpecific()) {
      Route route = routes.findById(tariff.routeNumber()).orElseThrow(
          () -> new ValidationException("tariff for unknown route " + tariff.routeNumber()));
      if (!route.servesDirection(tariff.fromCityId(), tariff.toCityId())) {
        throw new ValidationException("route " + route.number() + " does not go "
            + tariff.fromCityId() + " -> " + tariff.toCityId());
      }
    }
  }

  private void requireCity(String cityId) {
    if (!cities.existsById(cityId)) {
      throw new ValidationException("tariff for unknown city " + cityId);
    }
  }
}
