package transport.service.tariff;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import transport.config.AppConfig;
import transport.model.City;
import transport.model.Route;
import transport.repository.json.JsonStorage;
import transport.service.TariffService;

/** Кожна пара зупинок кожного seed-маршруту має додатну ціну квитка й багажу. */
class SeedTariffCoverageTest {

  @Test
  void everySeedLegIsPriced() {
    JsonStorage seed = JsonStorage.open(Path.of("data"));
    TariffPolicy chain = FallbackTariffPolicy.tableThenDistance(
        new TariffService(seed.tariffs(), seed.cities(), seed.routes()),
        AppConfig.load(Path.of("data")).tariff());

    int legs = 0;
    for (Route route : seed.routes().findAll()) {
      List<City> stops = route.stops();
      for (int i = 0; i < stops.size(); i++) {
        for (int j = i + 1; j < stops.size(); j++) {
          String from = stops.get(i).id();
          String to = stops.get(j).id();
          assertThat(chain.requireTicketPrice(route, from, to).signum()).isEqualTo(1);
          assertThat(chain.requireLuggagePrice(route, from, to, 10).signum()).isEqualTo(1);
          legs++;
        }
      }
    }
    assertThat(legs > 0).isEqualTo(true);
  }
}
