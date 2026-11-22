package transport.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import transport.model.Bus;
import transport.model.City;
import transport.model.Route;
import transport.model.RouteSegment;

class LowerBoundTest {

  private static final City KYIV = new City("K", "Kyiv", 50.4501, 30.5234);
  private static final City ZHYTOMYR = new City("Z", "Zhytomyr", 50.2547, 28.6587);
  private static final City RIVNE = new City("R", "Rivne", 50.6199, 26.2516);
  private static final City ODESA = new City("O", "Odesa", 46.4825, 30.7233);
  private static final City MYKOLAIV = new City("M", "Mykolaiv", 46.9750, 31.9946);

  // при 60 км/год підгін у хвилинах дорівнює кілометрам
  private static final Route K_Z = route(1, KYIV, ZHYTOMYR, 120, 120);
  private static final Route Z_K = route(2, ZHYTOMYR, KYIV, 120, 120);
  private static final Route R_Z = route(3, RIVNE, ZHYTOMYR, 60, 60);
  private static final Route O_M = route(4, ODESA, MYKOLAIV, 130, 130);

  private static final CityGraph GRAPH = CityGraph.fromRoutes(List.of(K_Z, Z_K, R_Z, O_M));
  private static final ScheduleConstraints CONSTRAINTS =
      new ScheduleConstraints(Duration.ofMinutes(30), Duration.ofHours(10), 60);
  private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

  private final List<TripDemand> demands = new ArrayList<>();

  private static Route route(int number, City from, City to, double km, int minutes) {
    return new Route(
        number, from.id() + "-" + to.id(),
        List.of(new RouteSegment(from, to, km, Duration.ofMinutes(minutes))));
  }

  private void trip(Route route, String time, int flexMinutes) {
    LocalDateTime departure = LocalDateTime.of(DAY, LocalTime.parse(time));
    demands.add(
        new TripDemand(
            "T" + (demands.size() + 1), route, departure, Duration.ofMinutes(flexMinutes), 1, 20,
            0));
  }

  private int minBuses() {
    Bus bus = new Bus("B1", "Setra", 50, 1500, "K");
    return LowerBound.minBuses(new ProblemInstance(GRAPH, List.of(bus), demands, CONSTRAINTS));
  }

  @Test
  void noTripsNeedNoBuses() {
    assertThat(minBuses()).isZero();
  }

  @Test
  void mathModelExample() {
    trip(K_Z, "08:00", 30);
    trip(Z_K, "10:15", 30);
    trip(R_Z, "10:30", 0);
    assertThat(minBuses()).isEqualTo(2);
  }

  @Test
  void chainNeedsOneBus() {
    trip(K_Z, "08:00", 0);
    trip(Z_K, "10:30", 0);
    trip(K_Z, "13:00", 0);
    assertThat(minBuses()).isEqualTo(1);
  }

  @Test
  void flexMakesTurnaroundPossible() {
    trip(K_Z, "08:00", 0);
    trip(Z_K, "10:15", 0);
    assertThat(minBuses()).isEqualTo(2);

    demands.clear();
    trip(K_Z, "08:00", 0);
    trip(Z_K, "10:15", 15);
    assertThat(minBuses()).isEqualTo(1);
  }

  @Test
  void deadheadIsCounted() {
    // з Z до R 60 хв підгону, тож R-Z не раніше 11:30
    trip(K_Z, "08:00", 0);
    trip(R_Z, "11:29", 0);
    assertThat(minBuses()).isEqualTo(2);

    demands.clear();
    trip(K_Z, "08:00", 0);
    trip(R_Z, "11:30", 0);
    assertThat(minBuses()).isEqualTo(1);
  }

  @Test
  void unreachableCityNeedsAnotherBus() {
    trip(K_Z, "08:00", 0);
    trip(O_M, "20:00", 0);
    assertThat(minBuses()).isEqualTo(2);
  }

  @Test
  void parallelTripsNeedOneBusEach() {
    trip(K_Z, "08:00", 0);
    trip(K_Z, "08:00", 0);
    trip(K_Z, "08:30", 0);
    assertThat(minBuses()).isEqualTo(3);
  }

  @Test
  void wideWindowsInBothDirectionsStillNeedOneBus() {
    // кожен рейс може йти після іншого, пари утворюють цикл
    trip(K_Z, "12:00", 240);
    trip(Z_K, "12:00", 240);
    assertThat(minBuses()).isEqualTo(1);
  }

  static List<ExperimentSize> sizes() {
    return ExperimentSize.ALL;
  }

  @ParameterizedTest
  @MethodSource("sizes")
  void notAboveReferenceSchedule(ExperimentSize size) {
    for (long seed = 1; seed <= 20; seed++) {
      InstanceGenerator.Generated generated =
          new InstanceGenerator(seed).generateWithReference(size);
      assertThat(LowerBound.minBuses(generated.problem()))
          .isBetween(1, generated.reference().usedBusIds().size());
    }
  }
}
