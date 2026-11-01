package transport.optimization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import transport.model.Bus;
import transport.model.BusStatus;
import transport.model.City;
import transport.model.Route;
import transport.model.RouteSegment;
import transport.model.TimeWindow;

class ConflictCheckerTest {

  private static final City KYIV = new City("KYIV", "Kyiv", 50.4501, 30.5234);
  private static final City ZHYTOMYR = new City("ZHYTOMYR", "Zhytomyr", 50.2547, 28.6587);
  private static final City RIVNE = new City("RIVNE", "Rivne", 50.6199, 26.2516);
  private static final City ODESA = new City("ODESA", "Odesa", 46.4825, 30.7233);
  private static final City MYKOLAIV = new City("MYKOLAIV", "Mykolaiv", 46.9750, 31.9946);

  // при 60 км/год підгін у хвилинах дорівнює кілометрам
  private static final Route KYIV_ZHYTOMYR = route(1, KYIV, ZHYTOMYR, 120, 120);
  private static final Route ZHYTOMYR_KYIV = route(2, ZHYTOMYR, KYIV, 120, 120);
  private static final Route RIVNE_ZHYTOMYR = route(3, RIVNE, ZHYTOMYR, 60, 60);
  private static final Route ODESA_MYKOLAIV = route(4, ODESA, MYKOLAIV, 130, 130);

  private static final CityGraph GRAPH =
      CityGraph.fromRoutes(List.of(KYIV_ZHYTOMYR, ZHYTOMYR_KYIV, RIVNE_ZHYTOMYR, ODESA_MYKOLAIV));
  private static final ScheduleConstraints CONSTRAINTS =
      new ScheduleConstraints(Duration.ofMinutes(30), Duration.ofHours(10), 60);
  private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

  private final ConflictChecker checker = new ConflictChecker();
  private final List<TripDemand> demands = new ArrayList<>();
  private final List<Assignment> assignments = new ArrayList<>();

  private static Route route(int number, City from, City to, double km, int minutes) {
    return new Route(
        number, from.id() + "-" + to.id(),
        List.of(new RouteSegment(from, to, km, Duration.ofMinutes(minutes))));
  }

  private static Bus bus(String depotCityId) {
    return new Bus("BUS1", "Setra", 50, 1500, depotCityId);
  }

  private static LocalDateTime at(String time) {
    return LocalDateTime.of(DAY, LocalTime.parse(time));
  }

  private void trip(String id, Route route, LocalDateTime departure, int passengers, double kg) {
    demands.add(
        new TripDemand(id, route, departure, Duration.ofMinutes(30), 1, passengers, kg));
    assignments.add(new Assignment(id, "BUS1", departure));
  }

  private void trip(String id, Route route, String time) {
    trip(id, route, at(time), 40, 500);
  }

  private List<Conflict> check(Bus bus) {
    ProblemInstance problem = new ProblemInstance(GRAPH, List.of(bus), demands, CONSTRAINTS);
    Schedule schedule = new Schedule(demands.stream().map(TripDemand::id).toList());
    assignments.forEach(schedule::assign);
    return checker.check(schedule, problem);
  }

  private List<ConflictType> conflicts(Bus bus) {
    return check(bus).stream().map(Conflict::type).toList();
  }

  private List<String> described() {
    return check(bus("KYIV")).stream().map(c -> c.type() + " " + c.description()).toList();
  }

  private List<ConflictType> conflicts() {
    return conflicts(bus("KYIV"));
  }

  static Stream<Arguments> secondTripAfterKyivZhytomyrAt8() {
    // перший рейс 08:00-10:00, автобус у Zhytomyr, розворот 30 хв
    return Stream.of(
        Arguments.of("overlap", ZHYTOMYR_KYIV, "09:30", List.of(ConflictType.OVERLAP)),
        Arguments.of("short turnaround", ZHYTOMYR_KYIV, "10:29", List.of(ConflictType.TURNAROUND)),
        Arguments.of("exact turnaround", ZHYTOMYR_KYIV, "10:30", List.of()),
        Arguments.of("short deadhead", RIVNE_ZHYTOMYR, "11:29",
            List.of(ConflictType.WRONG_LOCATION)),
        Arguments.of("exact deadhead", RIVNE_ZHYTOMYR, "11:30", List.of()),
        Arguments.of("unreachable", ODESA_MYKOLAIV, "18:00",
            List.of(ConflictType.WRONG_LOCATION)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("secondTripAfterKyivZhytomyrAt8")
  void secondTrip(String name, Route route, String time, List<ConflictType> expected) {
    trip("D1", KYIV_ZHYTOMYR, "08:00");
    trip("D2", route, time);
    assertThat(conflicts()).containsExactlyElementsOf(expected);
  }

  @Test
  void readmeExampleIsOverlap() {
    Route long1 = route(5, KYIV, ZHYTOMYR, 120, 210);
    Route long2 = route(6, ZHYTOMYR, KYIV, 120, 210);
    trip("R1", long1, "08:00");
    trip("R2", long2, "10:30");
    assertThat(conflicts()).containsExactly(ConflictType.OVERLAP);
  }

  @Test
  void longTripOverlapsSeveralLaterTrips() {
    trip("A", route(5, KYIV, ZHYTOMYR, 120, 360), "08:00");
    trip("B", ZHYTOMYR_KYIV, "09:00");
    trip("C", KYIV_ZHYTOMYR, "11:30");
    assertThat(described()).containsExactly("OVERLAP A -> B", "OVERLAP A -> C");
  }

  @Test
  void nextTripCountsFromTripThatEndsLast() {
    // B закінчується о 10:00, але автобус вільний лише після A о 14:00
    trip("A", route(5, KYIV, ZHYTOMYR, 120, 360), "08:00");
    trip("B", RIVNE_ZHYTOMYR, "09:00");
    trip("D", ZHYTOMYR_KYIV, "14:10");
    assertThat(described()).containsExactly("OVERLAP A -> B", "TURNAROUND A -> D");
  }

  @Test
  void firstTripNeedsDeadheadFromDepot() {
    trip("D1", ZHYTOMYR_KYIV, "01:59");
    assertThat(conflicts(bus("KYIV"))).containsExactly(ConflictType.WRONG_LOCATION);
  }

  @Test
  void firstTripWithExactDeadheadFromDepot() {
    trip("D1", ZHYTOMYR_KYIV, "02:00");
    assertThat(conflicts(bus("KYIV"))).isEmpty();
  }

  @Test
  void depotUnreachable() {
    trip("D1", ODESA_MYKOLAIV, "12:00");
    assertThat(conflicts(bus("KYIV"))).containsExactly(ConflictType.WRONG_LOCATION);
  }

  @Test
  void workingDayExactlyAtLimitIncludingDeadhead() {
    // 120 хв підгону з депо + 4 рейси по 120 хв = 10 год
    trip("D1", KYIV_ZHYTOMYR, "08:00");
    trip("D2", ZHYTOMYR_KYIV, "10:30");
    trip("D3", KYIV_ZHYTOMYR, "13:00");
    trip("D4", ZHYTOMYR_KYIV, "15:30");
    assertThat(conflicts(bus("ZHYTOMYR"))).isEmpty();
  }

  @Test
  void workingDayOverLimit() {
    trip("D1", KYIV_ZHYTOMYR, "08:00");
    trip("D2", ZHYTOMYR_KYIV, "10:30");
    trip("D3", KYIV_ZHYTOMYR, "13:00");
    trip("D4", ZHYTOMYR_KYIV, "15:30");
    trip("D5", KYIV_ZHYTOMYR, "18:00");
    assertThat(conflicts(bus("ZHYTOMYR"))).containsExactly(ConflictType.WORKING_HOURS);
  }

  @Test
  void nightTripOverlapsNextDay() {
    trip("D1", KYIV_ZHYTOMYR, "23:00");
    trip("D2", ZHYTOMYR_KYIV, at("00:30").plusDays(1), 40, 500);
    assertThat(conflicts(bus("ZHYTOMYR"))).containsExactly(ConflictType.OVERLAP);
  }

  @Test
  void afterNightTripBusIsNotBackInDepot() {
    trip("D1", KYIV_ZHYTOMYR, "23:00");
    trip("D2", KYIV_ZHYTOMYR, at("01:00").plusDays(1), 40, 500);
    assertThat(conflicts(bus("KYIV"))).containsExactly(ConflictType.WRONG_LOCATION);
  }

  @Test
  void turnaroundAfterNightTrip() {
    trip("D1", KYIV_ZHYTOMYR, "23:00");
    trip("D2", ZHYTOMYR_KYIV, at("01:00").plusDays(1), 40, 500);
    assertThat(conflicts(bus("ZHYTOMYR"))).containsExactly(ConflictType.TURNAROUND);
  }

  @Test
  void nextDayStartsWhereBusEnded() {
    trip("D1", route(5, KYIV, ZHYTOMYR, 120, 480), "15:00");
    trip("D2", ZHYTOMYR_KYIV, at("00:00").plusDays(1), 40, 500);
    assertThat(conflicts(bus("KYIV"))).isEmpty();
  }

  @Test
  void busUnavailableDuringDeadheadFromDepot() {
    trip("D1", KYIV_ZHYTOMYR, "10:00");
    Bus bus =
        new Bus(
            "BUS1", "Setra", 50, 1500, "ZHYTOMYR", BusStatus.AVAILABLE,
            List.of(new TimeWindow(at("08:00"), at("10:00"))));
    assertThat(conflicts(bus)).containsExactly(ConflictType.BUS_UNAVAILABLE);
  }

  @Test
  void busUnavailableDuringDeadheadBetweenTrips() {
    // підгін Zhytomyr - Rivne 60 хв, тобто 10:30-11:30
    trip("D1", KYIV_ZHYTOMYR, "08:00");
    trip("D2", RIVNE_ZHYTOMYR, "11:30");
    Bus bus = unavailableBus(at("10:30"), at("11:00"));
    assertThat(conflicts(bus)).containsExactly(ConflictType.BUS_UNAVAILABLE);
  }

  @Test
  void busInMaintenance() {
    trip("D1", KYIV_ZHYTOMYR, "08:00");
    Bus bus = new Bus("BUS1", "Setra", 50, 1500, "KYIV", BusStatus.MAINTENANCE, List.of());
    assertThat(conflicts(bus)).containsExactly(ConflictType.BUS_UNAVAILABLE);
  }

  @Test
  void tripHitsUnavailablePeriod() {
    trip("D1", KYIV_ZHYTOMYR, "08:00");
    Bus bus = unavailableBus(at("09:00"), at("12:00"));
    assertThat(conflicts(bus)).containsExactly(ConflictType.BUS_UNAVAILABLE);
  }

  @Test
  void unavailablePeriodRightAfterTrip() {
    trip("D1", KYIV_ZHYTOMYR, "08:00");
    Bus bus = unavailableBus(at("10:00"), at("12:00"));
    assertThat(conflicts(bus)).isEmpty();
  }

  private static Bus unavailableBus(LocalDateTime from, LocalDateTime to) {
    return new Bus(
        "BUS1", "Setra", 50, 1500, "KYIV", BusStatus.AVAILABLE,
        List.of(new TimeWindow(from, to)));
  }

  @Test
  void tooManyPassengers() {
    trip("D1", KYIV_ZHYTOMYR, at("08:00"), 51, 500);
    assertThat(conflicts()).containsExactly(ConflictType.CAPACITY);
  }

  @Test
  void tooMuchLuggage() {
    trip("D1", KYIV_ZHYTOMYR, at("08:00"), 40, 1500.1);
    assertThat(conflicts()).containsExactly(ConflictType.CAPACITY);
  }

  @Test
  void exactCapacityIsFine() {
    trip("D1", KYIV_ZHYTOMYR, at("08:00"), 50, 1500);
    assertThat(conflicts()).isEmpty();
  }

  private ProblemInstance problem() {
    return new ProblemInstance(GRAPH, List.of(bus("KYIV")), demands, CONSTRAINTS);
  }

  private Schedule scheduleWithFirstTrip() {
    Schedule schedule = new Schedule(demands.stream().map(TripDemand::id).toList());
    schedule.assign(assignments.get(0));
    return schedule;
  }

  @Test
  void canAssignDoesNotChangeSchedule() {
    trip("D1", KYIV_ZHYTOMYR, "08:00");
    trip("D2", ZHYTOMYR_KYIV, "10:30");
    Schedule schedule = scheduleWithFirstTrip();

    assertThat(checker.canAssign(schedule, assignments.get(1), problem())).isTrue();
    assertThat(schedule.assignmentOf("D2")).isEmpty();
  }

  @Test
  void canAssignRejectsConflict() {
    trip("D1", KYIV_ZHYTOMYR, "08:00");
    trip("D2", ZHYTOMYR_KYIV, "10:20");
    assertThat(checker.canAssign(scheduleWithFirstTrip(), assignments.get(1), problem()))
        .isFalse();
  }

  @Test
  void canAssignRejectsTimeOutsideFlex() {
    trip("D1", KYIV_ZHYTOMYR, "08:00");
    trip("D2", ZHYTOMYR_KYIV, "12:00");
    Assignment tooLate = new Assignment("D2", "BUS1", at("12:31"));
    assertThat(checker.canAssign(scheduleWithFirstTrip(), tooLate, problem())).isFalse();
  }

  @Test
  void canAssignMovesAlreadyAssignedTrip() {
    trip("D1", KYIV_ZHYTOMYR, "08:00");
    trip("D2", ZHYTOMYR_KYIV, "10:40");
    Schedule schedule = scheduleWithFirstTrip();
    schedule.assign(new Assignment("D2", "BUS1", at("10:20")));

    Assignment later = new Assignment("D2", "BUS1", at("10:40"));
    assertThat(checker.canAssign(schedule, later, problem())).isTrue();
  }

  @Test
  void unknownIdsAreRejected() {
    trip("D1", KYIV_ZHYTOMYR, "08:00");
    Schedule schedule = new Schedule(List.of("D1"));
    assertThatThrownBy(
            () -> checker.canAssign(schedule, new Assignment("D1", "NOPE", at("08:00")), problem()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> checker.canAssign(schedule, new Assignment("X", "BUS1", at("08:00")), problem()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
