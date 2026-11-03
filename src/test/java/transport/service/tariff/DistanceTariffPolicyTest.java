package transport.service.tariff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import transport.config.TariffConfig;
import transport.exception.ValidationException;
import transport.model.Route;

class DistanceTariffPolicyTest {

  private final Route route = TariffTestData.route12();
  private final DistanceTariffPolicy policy = new DistanceTariffPolicy(TariffConfig.DEFAULT);

  // 50 + 1.2 × (140 + 190) = 446
  @Test
  void ticketPriceGrowsWithDistance() {
    assertThat(policy.ticketPrice(route, "KYIV", "RIVNE")).contains(new BigDecimal("446"));
    assertThat(policy.ticketPrice(route, "RIVNE", "LUTSK")).contains(new BigDecimal("140"));
  }

  // 0.01 × 18 × 405 = 72.9 → 73
  @Test
  void luggagePriceUsesWeightAndDistance() {
    assertThat(policy.luggagePrice(route, "KYIV", "LUTSK", 18)).contains(new BigDecimal("73"));
  }

  @Test
  void priceScaleKeepsKopecks() {
    TariffConfig kopecks = new TariffConfig(
        BigDecimal.ZERO, new BigDecimal("0.333"), new BigDecimal("0.01"), 2);

    assertThat(new DistanceTariffPolicy(kopecks).ticketPrice(route, "RIVNE", "LUTSK"))
        .contains(new BigDecimal("24.98"));
  }

  @Test
  void rejectsBadInput() {
    assertThatThrownBy(() -> policy.ticketPrice(route, "LUTSK", "KYIV"))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> policy.luggagePrice(route, "KYIV", "LUTSK", 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
