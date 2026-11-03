package transport.service.tariff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import transport.exception.ValidationException;
import transport.model.Route;
import transport.model.Tariff;

class TableTariffPolicyTest {

  private final Route route = TariffTestData.route12();
  private final TableTariffPolicy policy = new TableTariffPolicy(TariffTestData.tariffService(
      Tariff.general("KYIV", "RIVNE", new BigDecimal("450"), new BigDecimal("2")),
      new Tariff("KYIV", "LUTSK", 12, new BigDecimal("520"), new BigDecimal("2.35")),
      Tariff.general("KYIV", "LUTSK", new BigDecimal("550"), new BigDecimal("2"))), 0);

  @Test
  void usesRouteTariffBeforeGeneral() {
    assertThat(policy.ticketPrice(route, "KYIV", "LUTSK")).contains(new BigDecimal("520"));
    assertThat(policy.ticketPrice(route, "KYIV", "RIVNE")).contains(new BigDecimal("450"));
  }

  @Test
  void emptyWhenTableHasNoPrice() {
    assertThat(policy.ticketPrice(route, "RIVNE", "LUTSK")).isEmpty();
    assertThat(policy.luggagePrice(route, "RIVNE", "LUTSK", 10)).isEmpty();
  }

  // 2.35 × 18 = 42.3 → 42 при priceScale = 0
  @Test
  void luggagePriceIsRounded() {
    assertThat(policy.luggagePrice(route, "KYIV", "LUTSK", 18)).contains(new BigDecimal("42"));
  }

  @Test
  void rejectsLegRouteDoesNotServe() {
    assertThatThrownBy(() -> policy.ticketPrice(route, "LUTSK", "KYIV"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("does not go");
  }
}
