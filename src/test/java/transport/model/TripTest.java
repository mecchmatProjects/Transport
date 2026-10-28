package transport.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import transport.exception.InvalidStatusTransitionException;

class TripTest {

  private static final Duration TURNAROUND = Duration.ofHours(1);

  @Test
  void newTripIsScheduledWithoutBus() {
    Trip trip = TestData.trip("T1", LocalTime.of(8, 0));
    assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED);
    assertThat(trip.hasBus()).isFalse();
  }

  @Test
  void arrivalAndStopTimes() {
    Trip trip = TestData.trip("T1", LocalTime.of(8, 0));
    assertThat(trip.arrival()).isEqualTo(LocalDateTime.of(2026, 9, 23, 14, 0));
    assertThat(trip.arrivalAt("ZHYTOMYR")).isEqualTo(LocalDateTime.of(2026, 9, 23, 10, 0));
    assertThat(trip.departureFrom("ZHYTOMYR")).isEqualTo(LocalDateTime.of(2026, 9, 23, 10, 10));

    List<ScheduledStop> stops = trip.scheduledStops();
    assertThat(stops).hasSize(4);
    assertThat(stops.get(2).arrival()).isEqualTo(LocalDateTime.of(2026, 9, 23, 12, 40));
    assertThat(stops.get(2).departure()).isEqualTo(LocalDateTime.of(2026, 9, 23, 12, 50));
  }

  @Test
  void overnightTripEndsNextDay() {
    Trip trip = TestData.trip("T1", LocalTime.of(22, 0));
    assertThat(trip.arrival()).isEqualTo(LocalDateTime.of(2026, 9, 24, 4, 0));
  }

  @Test
  void overlappingTripsConflict() {
    // README §9: 08:00–14:00 і 10:30–16:30 одним автобусом — заборонено
    Trip first = TestData.trip("T1", LocalTime.of(8, 0));
    Trip second = TestData.trip("T2", LocalTime.of(10, 30));
    assertThat(first.conflictsWith(second, TURNAROUND)).isTrue();
    assertThat(second.conflictsWith(first, TURNAROUND)).isTrue();
  }

  @Test
  void turnaroundIsRespected() {
    Trip first = TestData.trip("T1", LocalTime.of(8, 0)); // до 14:00
    Trip tooEarly = TestData.trip("T2", LocalTime.of(14, 30));
    Trip exactlyAfterTurnaround = TestData.trip("T3", LocalTime.of(15, 0));
    assertThat(first.conflictsWith(tooEarly, TURNAROUND)).isTrue();
    assertThat(first.conflictsWith(exactlyAfterTurnaround, TURNAROUND)).isFalse();
    assertThat(exactlyAfterTurnaround.conflictsWith(first, TURNAROUND)).isFalse();
  }

  @Test
  void tripDoesNotConflictWithItself() {
    Trip trip = TestData.trip("T1", LocalTime.of(8, 0));
    assertThat(trip.conflictsWith(trip, TURNAROUND)).isFalse();
  }

  @Test
  void busCanBeAssignedOnlyBeforeDeparture() {
    Trip trip = TestData.trip("T1", LocalTime.of(8, 0));
    trip.assignBus("AA1234");
    assertThat(trip.getBusId()).isEqualTo("AA1234");

    trip.changeStatus(TripStatus.IN_PROGRESS);
    assertThatThrownBy(() -> trip.assignBus("BB0001")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> trip.reschedule(LocalTime.NOON))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void statusTransitions() {
    Trip trip = TestData.trip("T1", LocalTime.of(8, 0));
    trip.changeStatus(TripStatus.IN_PROGRESS);
    trip.changeStatus(TripStatus.COMPLETED);
    assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
    assertThatThrownBy(() -> trip.changeStatus(TripStatus.CANCELLED))
        .isInstanceOf(InvalidStatusTransitionException.class)
        .hasMessageContaining("COMPLETED -> CANCELLED");
  }

  @Test
  void unknownStopIsRejected() {
    Trip trip = TestData.trip("T1", LocalTime.of(8, 0));
    assertThatThrownBy(() -> trip.arrivalAt("ODESA")).isInstanceOf(IllegalArgumentException.class);
  }
}
