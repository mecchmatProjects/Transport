package transport.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BusAndTariffTest {

  private static TimeWindow window(int fromHour, int toHour) {
    return new TimeWindow(
        LocalDateTime.of(2026, 9, 23, fromHour, 0), LocalDateTime.of(2026, 9, 23, toHour, 0));
  }

  @Test
  void touchingWindowsDoNotOverlap() {
    assertThat(window(8, 12).overlaps(window(12, 16))).isFalse();
    assertThat(window(8, 12).overlaps(window(11, 16))).isTrue();
    assertThatThrownBy(() -> window(12, 8)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void busAvailability() {
    Bus bus = new Bus("AA1234", "Setra S 415", 52, 1500, "KYIV");
    assertThat(bus.isAvailableFor(window(8, 14))).isTrue();

    bus.addUnavailablePeriod(window(12, 18));
    assertThat(bus.isAvailableFor(window(8, 14))).isFalse();
    assertThat(bus.isAvailableFor(window(6, 12))).isTrue();

    bus.setStatus(BusStatus.MAINTENANCE);
    assertThat(bus.isAvailableFor(window(6, 12))).isFalse();
  }

  @Test
  void busValidationAndIdentity() {
    assertThatThrownBy(() -> new Bus("AA1234", "X", 0, 100, "KYIV"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new Bus("AA1234", "A", 52, 1500, "KYIV"))
        .isEqualTo(new Bus("AA1234", "B", 30, 800, "LVIV"));
  }

  @Test
  void busKeepsDepot() {
    Bus bus = new Bus("AA1234", "Setra S 415", 52, 1500, "KYIV");
    assertThat(bus.getDepotCityId()).isEqualTo("KYIV");
    assertThat(bus.getLuggageCapacityKg()).isEqualTo(1500.0);
    assertThatThrownBy(() -> new Bus("AA1234", "X", 52, 100, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void tariffMatching() {
    Tariff general = Tariff.general("KYIV", "RIVNE", new BigDecimal("450"), new BigDecimal("5"));
    Tariff route12 = new Tariff("KYIV", "RIVNE", 12, new BigDecimal("400"), new BigDecimal("4"));

    assertThat(general.appliesTo(12, "KYIV", "RIVNE")).isTrue();
    assertThat(general.appliesTo(7, "KYIV", "RIVNE")).isTrue();
    assertThat(route12.appliesTo(7, "KYIV", "RIVNE")).isFalse();
    assertThat(route12.isRouteSpecific()).isTrue();
    assertThat(general.appliesTo(12, "RIVNE", "KYIV")).isFalse();
    assertThat(general.luggagePrice(18)).isEqualByComparingTo(new BigDecimal("90"));
  }

  @Test
  void cityValidation() {
    assertThatThrownBy(() -> new City("X", "X", 91, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new City(" ", "X", 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
