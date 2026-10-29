package transport.optimization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleTest {

  private static final LocalDateTime DAY = LocalDateTime.of(2026, 11, 2, 0, 0);

  private static Assignment at(String demandId, String busId, int hour) {
    return new Assignment(demandId, busId, DAY.withHour(hour));
  }

  @Test
  void keepsAssignmentsOfBusSortedByDeparture() {
    Schedule schedule = new Schedule(List.of("D1", "D2", "D3"));
    schedule.assign(at("D1", "BUS1", 14));
    schedule.assign(at("D2", "BUS1", 8));
    schedule.assign(at("D3", "BUS1", 11));

    assertThat(schedule.assignmentsOf("BUS1"))
        .extracting(Assignment::demandId)
        .containsExactly("D2", "D3", "D1");
  }

  @Test
  void tracksUnassignedDemandsAndUsedBuses() {
    Schedule schedule = new Schedule(List.of("D1", "D2", "D3"));
    schedule.assign(at("D1", "BUS1", 8));
    schedule.assign(at("D2", "BUS2", 8));

    assertThat(schedule.unassignedDemandIds()).containsExactly("D3");
    assertThat(schedule.usedBusIds()).containsExactly("BUS1", "BUS2");
  }

  @Test
  void unassignReturnsRemovedAssignmentForUndo() {
    Schedule schedule = new Schedule(List.of("D1"));
    Assignment a = at("D1", "BUS1", 8);
    schedule.assign(a);

    assertThat(schedule.unassign("D1")).contains(a);
    assertThat(schedule.usedBusIds()).isEmpty();
    assertThat(schedule.unassign("D1")).isEmpty();

    schedule.assign(a);
    assertThat(schedule.assignmentOf("D1")).contains(a);
  }

  @Test
  void rejectsUnknownOrDuplicateDemand() {
    Schedule schedule = new Schedule(List.of("D1"));
    schedule.assign(at("D1", "BUS1", 8));

    assertThatThrownBy(() -> schedule.assign(at("D1", "BUS2", 9)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> schedule.assign(at("X", "BUS1", 9)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void copyIsIndependent() {
    Schedule schedule = new Schedule(List.of("D1", "D2"));
    schedule.assign(at("D1", "BUS1", 8));

    Schedule copy = schedule.copy();
    copy.assign(at("D2", "BUS1", 12));
    copy.unassign("D1");

    assertThat(schedule.assignmentsOf("BUS1"))
        .extracting(Assignment::demandId)
        .containsExactly("D1");
    assertThat(copy.assignmentsOf("BUS1")).extracting(Assignment::demandId).containsExactly("D2");
  }
}
