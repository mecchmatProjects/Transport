package transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import transport.exception.ValidationException;
import transport.model.Tariff;

class TariffServiceTest {

  private final ServiceFixture f = new ServiceFixture();
  private final TariffService service = f.tariffService;

  private final Tariff general =
      Tariff.general("KYIV", "RIVNE", new BigDecimal("450"), new BigDecimal("2"));
  private final Tariff onRoute12 =
      new Tariff("KYIV", "RIVNE", 12, new BigDecimal("420"), new BigDecimal("2"));

  @Test
  void routeTariffWinsOverGeneral() {
    service.create(general);
    service.create(onRoute12);

    assertThat(service.find(12, "KYIV", "RIVNE")).contains(onRoute12);
    assertThat(service.find(7, "KYIV", "RIVNE")).contains(general);
    assertThat(service.find(12, "RIVNE", "LUTSK")).isEmpty();
  }

  @Test
  void rejectsUnknownCityOrRoute() {
    assertThatThrownBy(() -> service.create(
        Tariff.general("KYIV", "LVIV", BigDecimal.TEN, BigDecimal.ONE)))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("LVIV");
    assertThatThrownBy(() -> service.create(
        new Tariff("KYIV", "RIVNE", 99, BigDecimal.TEN, BigDecimal.ONE)))
        .hasMessageContaining("route 99");
  }

  @Test
  void rejectsDirectionRouteDoesNotServe() {
    assertThatThrownBy(() -> service.create(
        new Tariff("LUTSK", "KYIV", 12, BigDecimal.TEN, BigDecimal.ONE)))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("does not go");
  }
}
