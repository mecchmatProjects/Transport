package transport.service.tariff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import transport.config.TariffConfig;
import transport.exception.ValidationException;
import transport.model.Route;
import transport.model.Tariff;
import transport.service.TariffService;

class FallbackTariffPolicyTest {

  private final Route route = TariffTestData.route12();
  private final TariffService tariffs = TariffTestData.tariffService(
      Tariff.general("KYIV", "RIVNE", new BigDecimal("450"), new BigDecimal("2")));
  private final TariffPolicy chain =
      FallbackTariffPolicy.tableThenDistance(tariffs, TariffConfig.DEFAULT);

  @Test
  void tableWinsWhenItHasPrice() {
    assertThat(chain.requireTicketPrice(route, "KYIV", "RIVNE")).isEqualTo(new BigDecimal("450"));
    assertThat(chain.requireLuggagePrice(route, "KYIV", "RIVNE", 10))
        .isEqualTo(new BigDecimal("20"));
  }

  @Test
  void distanceFillsGaps() {
    assertThat(chain.requireTicketPrice(route, "RIVNE", "LUTSK")).isEqualTo(new BigDecimal("140"));
  }

  @Test
  void tableAloneFailsLoudlyOnGap() {
    TariffPolicy table = new TableTariffPolicy(tariffs, 0);

    assertThatThrownBy(() -> table.requireTicketPrice(route, "RIVNE", "LUTSK"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("no tariff");
  }
}
