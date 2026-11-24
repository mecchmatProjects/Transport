package transport.optimization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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

class GreedyOptimizerTest {

  private static final City KYIV = new City("K", "Kyiv", 50.4501, 30.5234);
  private static final City ZHYTOMYR = new City("Z", "Zhytomyr", 50.2547, 28.6587);
  private static final City RIVNE = new City("R", "Rivne", 50.6199, 26.2516);

  // при 60 км/год підгін у хвилинах дорівнює кілометрам
  private static final Route K_Z = route(1, KYIV, ZHYTOMYR, 120, 120);
  private static final Route Z_K = route(2, ZHYTOMYR, KYIV, 120, 120);
  private static final Route R_Z = route(3, RIVNE, ZHYTOMYR, 60, 60);

  private static final CityGraph GRAPH = CityGraph.fromRoutes(List.of(K_Z, Z_K, R_Z));
  private static final ScheduleConstraints CONSTRAINTS =
      new ScheduleConstraints(Duration.ofMinutes(30), Duration.ofHours(10), 60);
  private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

  private final GreedyOptimizer greedy = new GreedyOptimizer((schedule, problem) -> 0);
  private final List<TripDemand> demands = new ArrayList<>();
  private final List<Bus> buses = new ArrayList<>();

  private static Route route(int number, City from, City to, double km, int minutes) {
    return new Route(
        number, from.id() + "-" + to.id(),
        List.of(new RouteSegment(from, to, km, Duration.ofMinutes(minutes))));
  }

  private static LocalDateTime at(String time) {
    return LocalDateTime.of(DAY, LocalTime.parse(time));
  }

  private void trip(String id, Route route, String time, int flex, int priority, int passengers) {
    demands.add(
        new TripDemand(id, route, at(time), Duration.ofMinutes(flex), priority, passengers, 0));
  }

  private void bus(String id, String depot) {
    buses.add(new Bus(id, "Setra", 50, 1500, depot));
  }

  private ProblemInstance problem() {
    return new ProblemInstance(GRAPH, buses, demands, CONSTRAINTS);
  }

  private void mathModelExample() {
    trip("t1", K_Z, "08:00", 30, 1, 40);
    trip("t2", Z_K, "10:15", 30, 1, 35);
    trip("t3", R_Z, "10:30", 0, 1, 20);
    bus("b1", "K");
    bus("b2", "R");
  }

  @Test
  void mathModelExampleUsesTwoBuses() {
    mathModelExample();
    Schedule schedule = greedy.optimize(problem()).schedule();

    assertThat(schedule.assignmentOf("t1")).contains(new Assignment("t1", "b1", at("08:00")));
    assertThat(schedule.assignmentOf("t2")).contains(new Assignment("t2", "b1", at("10:30")));
    assertThat(schedule.assignmentOf("t3")).contains(new Assignment("t3", "b2", at("10:30")));
  }

  @Test
  void mathModelExampleMetrics() {
    mathModelExample();
    ScheduleMetrics metrics = greedy.optimize(problem()).metrics();

    assertThat(metrics.busesUsed()).isEqualTo(2);
    assertThat(metrics.totalDistanceKm()).isEqualTo(300);
    assertThat(metrics.deadheadKm()).isZero();
    assertThat(metrics.idleMinutes()).isZero();
    assertThat(metrics.avgLoadFactor()).isCloseTo((0.8 + 0.7 + 0.4) / 3, within(1e-9));
    assertThat(metrics.conflicts()).isZero();
    assertThat(metrics.unassigned()).isZero();
  }

  @Test
  void costComesFromGivenFunction() {
    mathModelExample();
    GreedyOptimizer counting = new GreedyOptimizer((schedule, problem) -> 7.5);
    assertThat(counting.optimize(problem()).cost()).isEqualTo(7.5);
  }

  @Test
  void prefersBusAlreadyInUse() {
    // b2 з Z не встигає на t1 о 01:00, а t2 могли б узяти обидва
    trip("t1", K_Z, "01:00", 0, 1, 20);
    trip("t2", Z_K, "11:00", 0, 1, 20);
    bus("b2", "Z");
    bus("b1", "K");
    Schedule schedule = greedy.optimize(problem()).schedule();

    assertThat(schedule.usedBusIds()).containsExactly("b1");
  }

  @Test
  void picksBusThatWaitsLeast() {
    // обидва автобуси в Z, b1 з 08:00, b2 з 10:00
    trip("t1", K_Z, "06:00", 0, 1, 20);
    trip("t2", K_Z, "08:00", 0, 1, 20);
    trip("t3", Z_K, "12:00", 0, 1, 20);
    bus("b1", "K");
    bus("b2", "K");
    Schedule schedule = greedy.optimize(problem()).schedule();

    assertThat(schedule.assignmentOf("t3").map(Assignment::busId)).contains("b2");
  }

  @Test
  void notEnoughBusesLeavesTripsUnassigned() {
    trip("t1", K_Z, "08:00", 0, 1, 20);
    trip("t2", K_Z, "08:00", 0, 1, 20);
    trip("t3", K_Z, "08:00", 0, 1, 20);
    bus("b1", "K");
    bus("b2", "K");
    OptimizationResult result = greedy.optimize(problem());

    assertThat(result.schedule().unassignedDemandIds()).containsExactly("t3");
    assertThat(result.metrics().unassigned()).isEqualTo(1);
  }

  @Test
  void tooManyPassengersForEveryBus() {
    trip("t1", K_Z, "08:00", 0, 1, 51);
    bus("b1", "K");
    assertThat(greedy.optimize(problem()).schedule().unassignedDemandIds()).containsExactly("t1");
  }

  @Test
  void noTripsGiveEmptySchedule() {
    bus("b1", "K");
    OptimizationResult result = greedy.optimize(problem());

    assertThat(result.schedule().usedBusIds()).isEmpty();
    assertThat(result.metrics().busesUsed()).isZero();
    assertThat(result.metrics().avgLoadFactor()).isZero();
  }

  @Test
  void noBusesLeaveAllTripsUnassigned() {
    trip("t1", K_Z, "08:00", 0, 1, 20);
    trip("t2", Z_K, "11:00", 0, 1, 20);
    assertThat(greedy.optimize(problem()).schedule().unassignedDemandIds())
        .containsExactly("t1", "t2");
  }

  static List<ExperimentSize> sizes() {
    return ExperimentSize.ALL;
  }

  @ParameterizedTest
  @MethodSource("sizes")
  void generatedInstancesHaveNoConflicts(ExperimentSize size) {
    for (long seed = 1; seed <= 10; seed++) {
      ProblemInstance problem = new InstanceGenerator(seed).generate(size);
      Schedule schedule = greedy.optimize(problem).schedule();

      assertThat(new ConflictChecker().check(schedule, problem)).isEmpty();
      if (schedule.unassignedDemandIds().isEmpty()) {
        assertThat(schedule.usedBusIds()).hasSizeGreaterThanOrEqualTo(LowerBound.minBuses(problem));
      }
    }
  }
}
