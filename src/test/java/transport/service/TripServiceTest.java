package transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static transport.service.ServiceFixture.DATE;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import transport.exception.InvalidStatusTransitionException;
import transport.exception.ScheduleConflictException;
import transport.exception.ValidationException;
import transport.model.Bus;
import transport.model.BusStatus;
import transport.model.Route;
import transport.model.Trip;
import transport.model.TripStatus;

class TripServiceTest {

  private final ServiceFixture f = new ServiceFixture();
  private final TripService service = f.tripService;

  @Test
  void rejectsUnknownOrOutdatedRoute() {
    Trip unknown = new Trip("T9", ServiceFixture.kyivLutsk(99), DATE, LocalTime.of(8, 0));
    assertThatThrownBy(() -> service.create(unknown)).hasMessageContaining("unknown route");

    Route outdated = new Route(12, "Old name", f.route12.segments());
    Trip stale = new Trip("T8", outdated, DATE, LocalTime.of(8, 0));
    assertThatThrownBy(() -> service.create(stale))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("differs");
  }

  // Рейс 08:00–14:00; 13:00 перекривається, 14:00 вже ні (розворот перевіряє ConflictChecker)
  @Test
  void assignBusRejectsOverlap() {
    service.create(f.trip("T1", LocalTime.of(8, 0)));
    service.create(f.trip("T2", LocalTime.of(13, 0)));
    service.create(f.trip("T3", LocalTime.of(14, 0)));
    service.assignBus("T1", "AA1234");

    assertThatThrownBy(() -> service.assignBus("T2", "AA1234"))
        .isInstanceOf(ScheduleConflictException.class)
        .hasMessageContaining("T1");
    assertThat(service.assignBus("T3", "AA1234").getBusId()).isEqualTo("AA1234");
  }

  @Test
  void createChecksPreassignedBus() {
    service.create(f.trip("T1", LocalTime.of(8, 0)));
    service.assignBus("T1", "AA1234");
    Trip overlapping = f.trip("T2", LocalTime.of(9, 0));
    overlapping.assignBus("AA1234");

    assertThatThrownBy(() -> service.create(overlapping))
        .isInstanceOf(ScheduleConflictException.class);
  }

  @Test
  void assignBusRejectsBusInMaintenance() {
    f.buses.add(new Bus("CC0001", "MAN", 50, 1200, "KYIV", BusStatus.MAINTENANCE, List.of()));
    service.create(f.trip("T1", LocalTime.of(8, 0)));

    assertThatThrownBy(() -> service.assignBus("T1", "CC0001"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("MAINTENANCE");
  }

  @Test
  void cancelledTripFreesBus() {
    service.create(f.trip("T1", LocalTime.of(8, 0)));
    service.create(f.trip("T2", LocalTime.of(9, 0)));
    service.assignBus("T1", "AA1234");
    service.cancel("T1");

    assertThat(service.assignBus("T2", "AA1234").getBusId()).isEqualTo("AA1234");
    assertThat(service.get("T1").getStatus()).isEqualTo(TripStatus.CANCELLED);
  }

  @Test
  void cancelTwiceIsInvalidTransition() {
    service.create(f.trip("T1", LocalTime.of(8, 0)));
    service.cancel("T1");

    assertThatThrownBy(() -> service.cancel("T1"))
        .isInstanceOf(InvalidStatusTransitionException.class);
    assertThatThrownBy(() -> service.assignBus("T1", "AA1234"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void cannotDeleteTripWithAnyTicket() {
    service.create(f.trip("T1", LocalTime.of(8, 0)));
    f.tickets.add(f.ticket("TK1", "T1"));
    f.tickets.getById("TK1").markUsed();

    assertThatThrownBy(() -> service.delete("T1")).hasMessageContaining("tickets");

    f.tickets.deleteById("TK1");
    service.delete("T1");
    assertThat(service.list()).isEmpty();
  }

  @Test
  void updateCannotChangeStatusRouteOrBus() {
    f.routes.add(ServiceFixture.kyivLutsk(13));
    service.create(f.trip("T1", LocalTime.of(8, 0)));
    service.cancel("T1");

    Trip revived = new Trip("T1", f.route12, DATE, LocalTime.of(8, 0), null, TripStatus.SCHEDULED);
    assertThatThrownBy(() -> service.update(revived)).hasMessageContaining("status");

    service.create(f.trip("T2", LocalTime.of(8, 0)));
    Trip completed =
        new Trip("T2", f.route12, DATE, LocalTime.of(8, 0), null, TripStatus.COMPLETED);
    assertThatThrownBy(() -> service.update(completed)).hasMessageContaining("status");

    Trip otherRoute = new Trip("T2", f.routes.getById(13), DATE, LocalTime.of(8, 0));
    assertThatThrownBy(() -> service.update(otherRoute)).hasMessageContaining("route");

    Trip withBus = f.trip("T2", LocalTime.of(8, 0));
    withBus.assignBus("AA1234");
    assertThatThrownBy(() -> service.update(withBus)).hasMessageContaining("assignBus");

    assertThat(service.get("T1").getStatus()).isEqualTo(TripStatus.CANCELLED);
    assertThat(service.get("T2").getStatus()).isEqualTo(TripStatus.SCHEDULED);
  }

  // Мутація отриманого рейсу не потрапляє в репозиторій, навіть якщо update впав
  @Test
  void failedUpdateLeavesStoredTripUnchanged() {
    service.create(f.trip("T1", LocalTime.of(8, 0)));
    service.create(f.trip("T2", LocalTime.of(9, 0)));
    service.assignBus("T1", "AA1234");

    Trip t2 = service.get("T2");
    t2.assignBus("AA1234");
    assertThatThrownBy(() -> service.update(t2)).isInstanceOf(ValidationException.class);

    assertThat(service.get("T2").getBusId()).isNull();
  }

  @Test
  void updateReschedulesAndRechecksBus() {
    service.create(f.trip("T1", LocalTime.of(8, 0)));
    service.create(f.trip("T2", LocalTime.of(15, 0)));
    service.assignBus("T1", "AA1234");
    service.assignBus("T2", "AA1234");

    Trip earlier = service.get("T2");
    earlier.reschedule(LocalTime.of(12, 0));
    assertThatThrownBy(() -> service.update(earlier))
        .isInstanceOf(ScheduleConflictException.class);
    assertThat(service.get("T2").getDepartureTime()).isEqualTo(LocalTime.of(15, 0));

    Trip later = service.get("T2");
    later.reschedule(LocalTime.of(16, 0));
    service.update(later);
    assertThat(service.get("T2").getDepartureTime()).isEqualTo(LocalTime.of(16, 0));
  }

  @Test
  void queriesAreSortedByDeparture() {
    service.create(f.trip("T2", LocalTime.of(15, 0)));
    service.create(f.trip("T1", LocalTime.of(7, 0)));
    service.create(new Trip("T3", f.route12, DATE.plusDays(1), LocalTime.of(6, 0)));
    service.assignBus("T2", "AA1234");

    assertThat(service.findByDate(DATE)).extracting(Trip::getId).containsExactly("T1", "T2");
    assertThat(service.findByRoute(12)).extracting(Trip::getId)
        .containsExactly("T1", "T2", "T3");
    assertThat(service.findByBus("AA1234")).extracting(Trip::getId).containsExactly("T2");
    assertThat(service.findByBus("NOBUS")).isEmpty();
  }
}
