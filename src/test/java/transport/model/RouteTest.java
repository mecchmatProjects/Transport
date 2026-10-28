package transport.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static transport.model.TestData.KYIV;
import static transport.model.TestData.LUTSK;
import static transport.model.TestData.RIVNE;
import static transport.model.TestData.ZHYTOMYR;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteTest {

  private final Route route = TestData.kyivLutsk();

  @Test
  void stopsAreDerivedFromSegments() {
    assertThat(route.stops()).containsExactly(KYIV, ZHYTOMYR, RIVNE, LUTSK);
    assertThat(route.origin()).isEqualTo(KYIV);
    assertThat(route.destination()).isEqualTo(LUTSK);
    assertThat(route.stopCount()).isEqualTo(4);
  }

  @Test
  void indexOfAndDirection() {
    assertThat(route.indexOf("RIVNE")).isEqualTo(2);
    assertThat(route.indexOf("ODESA")).isEqualTo(-1);
    assertThat(route.servesDirection("ZHYTOMYR", "LUTSK")).isTrue();
    assertThat(route.servesDirection("LUTSK", "KYIV")).isFalse();
    assertThat(route.servesDirection("KYIV", "KYIV")).isFalse();
  }

  @Test
  void distances() {
    assertThat(route.totalDistanceKm()).isCloseTo(405, within(1e-9));
    assertThat(route.distanceKm("ZHYTOMYR", "LUTSK")).isCloseTo(265, within(1e-9));
  }

  @Test
  void timingIncludesDwellOnlyAtIntermediateStops() {
    assertThat(route.arrivalOffset(0)).isEqualTo(Duration.ZERO);
    assertThat(route.departureOffset(0)).isEqualTo(Duration.ZERO);
    assertThat(route.arrivalOffset(1)).isEqualTo(Duration.ofMinutes(120));
    assertThat(route.departureOffset(1)).isEqualTo(Duration.ofMinutes(130));
    assertThat(route.arrivalOffset(2)).isEqualTo(Duration.ofMinutes(280));
    assertThat(route.totalDuration()).isEqualTo(Duration.ofHours(6));
    assertThat(route.departureOffset(3)).isEqualTo(Duration.ofHours(6));
  }

  @Test
  void dwellIsPerStop() {
    // Житомир 5 хв, Рівне 25 хв
    Route custom =
        new Route(
            7,
            "custom",
            List.of(
                new RouteSegment(KYIV, ZHYTOMYR, 140, Duration.ofMinutes(120), Duration.ofMinutes(5)),
                new RouteSegment(ZHYTOMYR, RIVNE, 190, Duration.ofMinutes(150), Duration.ofMinutes(25)),
                new RouteSegment(RIVNE, LUTSK, 75, Duration.ofMinutes(70))));
    assertThat(custom.dwellAt(0)).isEqualTo(Duration.ZERO);
    assertThat(custom.dwellAt(1)).isEqualTo(Duration.ofMinutes(5));
    assertThat(custom.dwellAt(2)).isEqualTo(Duration.ofMinutes(25));
    assertThat(custom.departureOffset(1)).isEqualTo(Duration.ofMinutes(125));
    assertThat(custom.arrivalOffset(2)).isEqualTo(Duration.ofMinutes(275));
    assertThat(custom.departureOffset(2)).isEqualTo(Duration.ofMinutes(300));
    assertThat(custom.totalDuration()).isEqualTo(Duration.ofMinutes(370));
  }

  @Test
  void finalStopHasNoDwell() {
    List<RouteSegment> segments =
        List.of(new RouteSegment(KYIV, ZHYTOMYR, 140, Duration.ofHours(2), Duration.ofMinutes(10)));
    assertThatThrownBy(() -> new Route(1, "bad", segments))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("final stop");
  }

  @Test
  void travelTimeBetweenStops() {
    assertThat(route.travelTime("KYIV", "RIVNE")).isEqualTo(Duration.ofMinutes(280));
    assertThat(route.travelTime("ZHYTOMYR", "LUTSK")).isEqualTo(Duration.ofMinutes(230));
  }

  @Test
  void rejectsWrongDirection() {
    assertThatThrownBy(() -> route.distanceKm("LUTSK", "KYIV"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsDisconnectedSegments() {
    List<RouteSegment> broken =
        List.of(
            new RouteSegment(KYIV, ZHYTOMYR, 140, Duration.ofHours(2)),
            new RouteSegment(RIVNE, LUTSK, 75, Duration.ofHours(1)));
    assertThatThrownBy(() -> new Route(1, "broken", broken))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not connected");
  }

  @Test
  void rejectsRepeatedCity() {
    List<RouteSegment> loop =
        List.of(
            new RouteSegment(KYIV, ZHYTOMYR, 140, Duration.ofHours(2)),
            new RouteSegment(ZHYTOMYR, KYIV, 140, Duration.ofHours(2)));
    assertThatThrownBy(() -> new Route(1, "loop", loop))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("twice");
  }

  @Test
  void segmentsAreImmutable() {
    assertThatThrownBy(() -> route.segments().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void segmentBetweenUsesHaversine() {
    RouteSegment segment = RouteSegment.between(KYIV, LUTSK, 80);
    assertThat(segment.distanceKm()).isCloseTo(KYIV.distanceKmTo(LUTSK), within(1e-9));
    assertThat(segment.travelTime().toMinutes())
        .isEqualTo(Math.round(segment.distanceKm() / 80 * 60));
  }
}
