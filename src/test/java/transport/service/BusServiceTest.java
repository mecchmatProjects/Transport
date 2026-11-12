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
}
