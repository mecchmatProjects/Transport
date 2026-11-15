package transport.service.tariff;

import java.time.Duration;
import java.util.List;
import transport.model.City;
import transport.model.Route;
import transport.model.RouteSegment;
import transport.model.Tariff;
import transport.repository.InMemoryRepository;
import transport.repository.Repository;
import transport.repository.TariffKey;
import transport.service.TariffService;

/** Маршрут 12 Kyiv → Zhytomyr → Rivne → Lutsk: 140 + 190 + 75 = 405 км. */
final class TariffTestData {

  static final City KYIV = new City("KYIV", "Kyiv", 50.4501, 30.5234);
  static final City ZHYTOMYR = new City("ZHYTOMYR", "Zhytomyr", 50.2547, 28.6587);
  static final City RIVNE = new City("RIVNE", "Rivne", 50.6199, 26.2516);
  static final City LUTSK = new City("LUTSK", "Lutsk", 50.7472, 25.3254);

  private TariffTestData() {}

  static Route route12() {
    return Route.withUniformDwell(
        12,
        "Kyiv - Lutsk",
        List.of(
            new RouteSegment(KYIV, ZHYTOMYR, 140, Duration.ofMinutes(120)),
            new RouteSegment(ZHYTOMYR, RIVNE, 190, Duration.ofMinutes(150)),
            new RouteSegment(RIVNE, LUTSK, 75, Duration.ofMinutes(70))),
        Duration.ofMinutes(10));
  }

  static TariffService tariffService(Tariff... tariffs) {
    Repository<City, String> cities = new InMemoryRepository<>("City", City::id);
    List.of(KYIV, ZHYTOMYR, RIVNE, LUTSK).forEach(cities::add);
    Repository<Route, Integer> routes = new InMemoryRepository<>("Route", Route::number);
    routes.add(route12());
    TariffService service = new TariffService(
        new InMemoryRepository<>("Tariff", TariffKey::of), cities, routes);
    for (Tariff tariff : tariffs) {
      service.create(tariff);
    }
    return service;
  }

}
