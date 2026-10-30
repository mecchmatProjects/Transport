package transport.optimization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import transport.model.City;
import transport.model.Route;
import transport.model.RouteSegment;

class CityGraphTest {

  private static final City KYIV = new City("KYIV", "Kyiv", 50.4501, 30.5234);
  private static final City ZHYTOMYR = new City("ZHYTOMYR", "Zhytomyr", 50.2547, 28.6587);
  private static final City RIVNE = new City("RIVNE", "Rivne", 50.6199, 26.2516);
  private static final City LUTSK = new City("LUTSK", "Lutsk", 50.7472, 25.3254);
  private static final City CHERKASY = new City("CHERKASY", "Cherkasy", 49.4444, 32.0598);
  private static final City UMAN = new City("UMAN", "Uman", 48.7484, 30.2218);
  private static final City ODESA = new City("ODESA", "Odesa", 46.4825, 30.7233);
  private static final City MYKOLAIV = new City("MYKOLAIV", "Mykolaiv", 46.9750, 31.9946);

  private static RouteSegment seg(City from, City to, double km) {
    return new RouteSegment(from, to, km, Duration.ofMinutes(60));
  }

  private static Route route(int number, RouteSegment... segments) {
    return new Route(number, "R" + number, List.of(segments));
  }

  private static final CityGraph GRAPH =
      CityGraph.fromRoutes(
          List.of(
              route(1, seg(KYIV, ZHYTOMYR, 140), seg(ZHYTOMYR, RIVNE, 190), seg(RIVNE, LUTSK, 75)),
              route(2, seg(KYIV, RIVNE, 300)), // коротше, ніж через Zhytomyr (330)
              route(3, seg(KYIV, CHERKASY, 190), seg(CHERKASY, UMAN, 180)),
              route(4, seg(ODESA, MYKOLAIV, 130))));

  @Test
  void sameCityIsZero() {
    assertThat(GRAPH.shortestDistanceKm("KYIV", "KYIV")).isZero();
    assertThat(GRAPH.deadheadMinutes("KYIV", "KYIV", 60)).isZero();
  }

  @Test
  void picksShorterOfTwoPaths() {
    assertThat(GRAPH.shortestDistanceKm("KYIV", "RIVNE")).isEqualTo(300);
    assertThat(GRAPH.shortestDistanceKm("KYIV", "LUTSK")).isEqualTo(375);
  }

  @Test
  void edgesWorkAgainstRouteDirection() {
    assertThat(GRAPH.shortestDistanceKm("LUTSK", "KYIV")).isEqualTo(375);
  }

  @Test
  void pathAcrossDifferentRoutes() {
    // Lutsk → Rivne → Kyiv → Cherkasy → Uman: 75 + 300 + 190 + 180
    assertThat(GRAPH.shortestDistanceKm("LUTSK", "UMAN")).isEqualTo(745);
  }

  @Test
  void unreachableAndUnknownCitiesAreInfinite() {
    assertThat(GRAPH.shortestDistanceKm("KYIV", "ODESA")).isInfinite();
    assertThat(GRAPH.shortestDistanceKm("KYIV", "NOWHERE")).isInfinite();
    assertThat(GRAPH.shortestDistanceKm("NOWHERE", "KYIV")).isInfinite();
    assertThat(GRAPH.deadheadMinutes("KYIV", "ODESA", 60)).isEqualTo(Integer.MAX_VALUE);
    assertThat(GRAPH.contains("ODESA")).isTrue();
    assertThat(GRAPH.contains("NOWHERE")).isFalse();
  }

  @Test
  void deadheadRoundsUp() {
    // 75 км при 60 км/год = 75 хв; при 70 км/год = 64.3 → 65
    assertThat(GRAPH.deadheadMinutes("RIVNE", "LUTSK", 60)).isEqualTo(75);
    assertThat(GRAPH.deadheadMinutes("RIVNE", "LUTSK", 70)).isEqualTo(65);
  }

  @Test
  void deadheadIsNotInflatedByFloatingPointError() {
    CityGraph graph = CityGraph.fromRoutes(List.of(route(9, seg(KYIV, ZHYTOMYR, 31))));
    assertThat(graph.deadheadMinutes("KYIV", "ZHYTOMYR", 60)).isEqualTo(31);
  }

  @Test
  void rejectsNonPositiveSpeed() {
    assertThatThrownBy(() -> GRAPH.deadheadMinutes("KYIV", "RIVNE", 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
