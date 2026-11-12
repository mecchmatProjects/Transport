package transport.service.tariff;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import transport.config.AppConfig;
import transport.model.Route;
import transport.model.Tariff;
import transport.repository.json.JsonStorage;
import transport.service.TariffService;

/** DoD K2: ціна змінюється правкою tariffs.json / config.json, код той самий. */
class TariffChangeWithoutRecompileTest {

  @TempDir Path dir;

  @Test
  void editingTariffsJsonChangesTicketPrice() throws Exception {
    JsonStorage storage = JsonStorage.open(dir);
    List.of(TariffTestData.KYIV, TariffTestData.ZHYTOMYR, TariffTestData.RIVNE,
        TariffTestData.LUTSK).forEach(storage.cities()::add);
    storage.routes().add(TariffTestData.route12());
    storage.tariffs().add(
        Tariff.general("KYIV", "RIVNE", new BigDecimal("437"), new BigDecimal("2")));

    assertThat(priceKyivRivne()).isEqualTo(new BigDecimal("437"));

    Path file = dir.resolve(JsonStorage.TARIFFS);
    Files.writeString(file, Files.readString(file).replace("437", "512"));

    assertThat(priceKyivRivne()).isEqualTo(new BigDecimal("512"));
  }

  @Test
  void editingConfigJsonChangesDistancePrice() throws Exception {
    writeConfig(50);
    assertThat(distancePriceRivneLutsk()).isEqualTo(new BigDecimal("140"));

    writeConfig(100);
    assertThat(distancePriceRivneLutsk()).isEqualTo(new BigDecimal("190"));
  }

  private BigDecimal priceKyivRivne() {
    JsonStorage storage = JsonStorage.open(dir);
    TariffService tariffs =
        new TariffService(storage.tariffs(), storage.cities(), storage.routes());
    Route route = storage.routes().getById(12);
    return FallbackTariffPolicy.tableThenDistance(tariffs, AppConfig.load(dir).tariff())
        .requireTicketPrice(route, "KYIV", "RIVNE");
  }

  private BigDecimal distancePriceRivneLutsk() {
    return new DistanceTariffPolicy(AppConfig.load(dir).tariff())
        .requireTicketPrice(TariffTestData.route12(), "RIVNE", "LUTSK");
  }

  private void writeConfig(int baseFare) throws Exception {
    Files.writeString(dir.resolve(AppConfig.FILE_NAME), """
        {"tariff": {"baseFare": %d, "farePerKm": 1.2, "luggagePerKgPerKm": 0.01,
                    "priceScale": 0},
         "maxRoutes": 30}
        """.formatted(baseFare));
  }
}
