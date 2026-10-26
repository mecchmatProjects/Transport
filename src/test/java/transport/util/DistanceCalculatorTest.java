package transport.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class DistanceCalculatorTest {

  @Test
  void kyivToLvivIsAbout468Km() {
    double km = DistanceCalculator.haversineKm(50.4501, 30.5234, 49.8397, 24.0297);
    assertThat(km).isCloseTo(468, within(5.0));
  }

  @Test
  void samePointIsZero() {
    assertThat(DistanceCalculator.haversineKm(50.0, 30.0, 50.0, 30.0)).isZero();
  }
}
