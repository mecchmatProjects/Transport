package transport.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleTemplateTest {

  private static final LocalDate WEDNESDAY = LocalDate.of(2026, 9, 23);
  private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 27);

  private static ScheduleTemplate weekdays() {
    return new ScheduleTemplate(
        "ST12",
        12,
        List.of(LocalTime.of(14, 0), LocalTime.of(8, 0)),
        Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        Duration.ofMinutes(30),
        2,
        35,
        400);
  }

  @Test
  void expandsIntoTripsOnMatchingDay() {
    List<Trip> trips = weekdays().tripsOn(WEDNESDAY, TestData.kyivLutsk());
    assertThat(trips).hasSize(2);
    assertThat(trips.get(0).getId()).isEqualTo("ST12-20260923-0800");
    assertThat(trips.get(0).getDepartureTime()).isEqualTo(LocalTime.of(8, 0));
    assertThat(trips.get(1).getId()).isEqualTo("ST12-20260923-1400");
    assertThat(trips.get(0).hasBus()).isFalse();
    assertThat(trips.get(0).getStatus()).isEqualTo(TripStatus.SCHEDULED);
  }

  @Test
  void noTripsOnOtherDays() {
    assertThat(weekdays().runsOn(SUNDAY)).isFalse();
    assertThat(weekdays().tripsOn(SUNDAY, TestData.kyivLutsk())).isEmpty();
  }

  @Test
  void rejectsForeignRoute() {
    Route other = new Route(99, "other", TestData.kyivLutsk().segments());
    assertThatThrownBy(() -> weekdays().tripsOn(WEDNESDAY, other))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("route 12");
  }

  @Test
  void flexWindow() {
    LocalDateTime planned = WEDNESDAY.atTime(8, 0);
    assertThat(weekdays().withinFlex(planned, planned.plusMinutes(30))).isTrue();
    assertThat(weekdays().withinFlex(planned, planned.minusMinutes(30))).isTrue();
    assertThat(weekdays().withinFlex(planned, planned.plusMinutes(31))).isFalse();
  }

  @Test
  void validation() {
    assertThatThrownBy(
            () ->
                ScheduleTemplate.daily(
                    "X", 12, List.of(LocalTime.NOON, LocalTime.NOON), Duration.ZERO, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate");
    assertThatThrownBy(() -> ScheduleTemplate.daily("X", 12, List.of(), Duration.ZERO, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> ScheduleTemplate.daily("X", 12, List.of(LocalTime.NOON), Duration.ZERO, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void collectionsAreImmutable() {
    ScheduleTemplate template = weekdays();
    assertThat(template.departureTimes()).containsExactly(LocalTime.of(8, 0), LocalTime.of(14, 0));
    assertThatThrownBy(() -> template.days().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
