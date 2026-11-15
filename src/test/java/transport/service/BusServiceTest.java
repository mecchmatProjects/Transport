package transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static transport.service.ServiceFixture.DATE;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import transport.exception.ValidationException;
import transport.model.Bus;
import transport.model.BusStatus;
import transport.model.TimeWindow;
import transport.model.Trip;
import transport.model.TripStatus;

class BusServiceTest {

  private final ServiceFixture f = new ServiceFixture();
  private final BusService service = f.busService;

  @Test
  void depotMustExist() {
    assertThatThrownBy(() -> service.create(new Bus("BB0001", "MAN", 50, 1200, "NOWHERE")))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("depot");
  }

  @Test
  void cannotDeleteBusWithScheduledTrip() {
    Trip trip = f.trip("T1", LocalTime.of(8, 0));
    trip.assignBus("AA1234");
    f.trips.add(trip);

    assertThatThrownBy(() -> service.delete("AA1234"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("T1");
  }

  @Test
  void finishedTripsAlsoBlockDelete() {
    Trip trip = f.trip("T1", LocalTime.of(8, 0));
    trip.assignBus("AA1234");
    f.trips.add(trip);
    f.tripService.cancel("T1");

    assertThatThrownBy(() -> service.delete("AA1234")).hasMessageContaining("T1");

    service.create(new Bus("BB0001", "MAN", 50, 1200, "KYIV"));
    service.delete("BB0001");
    assertThat(service.list()).extracting(Bus::getId).containsExactly("AA1234");
  }

  @Test
  void availableSkipsBusyAndBrokenBuses() {
    service.create(new Bus("BB0001", "MAN", 50, 1200, "KYIV"));
    service.create(new Bus("CC0001", "MAN", 50, 1200, "KYIV", BusStatus.MAINTENANCE,
        List.of()));
    Trip trip = f.trip("T1", LocalTime.of(8, 0));
    trip.assignBus("AA1234");
    f.trips.add(trip);

    TimeWindow noon = new TimeWindow(DATE.atTime(12, 0), DATE.atTime(13, 0));
    TimeWindow evening = new TimeWindow(DATE.atTime(18, 0), DATE.atTime(19, 0));

    assertThat(service.available(noon)).extracting(Bus::getId).containsExactly("BB0001");
    assertThat(service.available(evening)).extracting(Bus::getId)
        .containsExactly("AA1234", "BB0001");
  }

  // Рев'ю PR #10, N4: зміна отриманого автобуса без update не зачіпає репозиторій
  @Test
  void getReturnsCopyOfBus() {
    service.get("AA1234").setStatus(BusStatus.MAINTENANCE);
    service.list().get(0).addUnavailablePeriod(new TimeWindow(
        DATE.atTime(6, 0), DATE.atTime(12, 0)));

    Bus stored = service.get("AA1234");
    assertThat(stored.getStatus()).isEqualTo(BusStatus.AVAILABLE);
    assertThat(stored.getUnavailablePeriods()).isEmpty();
  }

  @Test
  void updateAppliesChangeToCopy() {
    Bus bus = service.get("AA1234");
    bus.setStatus(BusStatus.MAINTENANCE);
    service.update(bus);

    bus.setStatus(BusStatus.AVAILABLE);

    assertThat(service.get("AA1234").getStatus()).isEqualTo(BusStatus.MAINTENANCE);
  }

  @Test
  void activeTripsAreCopies() {
    f.tripService.create(f.trip("T1", LocalTime.of(6, 0)));
    f.tripService.assignBus("T1", "AA1234");

    service.activeTripsOf("AA1234").get(0).changeStatus(TripStatus.CANCELLED);

    assertThat(service.activeTripsOf("AA1234")).extracting(Trip::getId).containsExactly("T1");
  }
}
