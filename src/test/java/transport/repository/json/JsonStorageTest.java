package transport.repository.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import transport.exception.StorageException;
import transport.model.Bus;
import transport.model.BusStatus;
import transport.model.City;
import transport.model.Contact;
import transport.model.Passenger;
import transport.model.PassengerLuggage;
import transport.model.Route;
import transport.model.RouteSegment;
import transport.model.ScheduleTemplate;
import transport.model.Shipment;
import transport.model.Tariff;
import transport.model.Ticket;
import transport.model.TicketStatus;
import transport.model.Trip;
import transport.model.TripStatus;
import transport.repository.TariffKey;

class JsonStorageTest {

  private static final City KYIV = new City("KYIV", "Kyiv", 50.4501, 30.5234);
  private static final City ZHYTOMYR = new City("ZHYTOMYR", "Zhytomyr", 50.2547, 28.6587);
  private static final City RIVNE = new City("RIVNE", "Rivne", 50.6199, 26.2516);

  private static final Route ROUTE =
      Route.withUniformDwell(
          12,
          "Kyiv - Rivne",
          List.of(
              new RouteSegment(KYIV, ZHYTOMYR, 140, Duration.ofMinutes(120)),
              new RouteSegment(ZHYTOMYR, RIVNE, 190, Duration.ofMinutes(150))),
          Duration.ofMinutes(10));

  private static final LocalDate DATE = LocalDate.of(2026, 9, 23);

  @TempDir Path dir;

  /** Заповнює всі 8 репозиторіїв і повертає квиток, змінений після продажу. */
  private static Ticket fill(JsonStorage storage) {
    List.of(KYIV, ZHYTOMYR, RIVNE).forEach(storage.cities()::add);
    storage.routes().add(ROUTE);

    Bus bus = new Bus("AA1234", "Setra S 515", 52, 1500, "KYIV");
    bus.setStatus(BusStatus.MAINTENANCE);
    storage.buses().add(bus);

    storage.tariffs().add(Tariff.general("KYIV", "RIVNE", new BigDecimal("450"), BigDecimal.ONE));
    storage.tariffs().add(new Tariff("KYIV", "RIVNE", 12, new BigDecimal("420"), BigDecimal.ONE));
    storage.scheduleTemplates().add(
        ScheduleTemplate.daily("R12", 12, List.of(LocalTime.of(8, 0)), Duration.ofMinutes(30), 40));

    Trip trip = new Trip("T1", ROUTE, DATE, LocalTime.of(8, 0));
    trip.assignBus("AA1234");
    storage.trips().add(trip);
    storage.trips().add(new Trip("T2", ROUTE, DATE, LocalTime.of(14, 30)));

    Passenger passenger = new Passenger("P1", "Ivan Petrenko", "+380501112233");
    Ticket ticket = new Ticket("TK-1", "T1", passenger, "KYIV", "RIVNE", 7,
        new BigDecimal("420.00"), LocalDateTime.of(2026, 9, 20, 12, 15));
    storage.tickets().add(ticket);
    ticket.cancel();
    storage.tickets().save(ticket); // зміна змінної сутності потрапляє у файл лише через save

    storage.luggage().add(new PassengerLuggage("L1", ticket, 18, 0.1, new BigDecimal("18")));
    storage.luggage().add(new Shipment("S1", "T1", "KYIV", "RIVNE", 5, 0.02,
        new BigDecimal("120"), new Contact("Olha", null), new Contact("Petro", "+380671234567")));
    return ticket;
  }

  @Test
  void everythingSurvivesReopen() {
    JsonStorage first = JsonStorage.open(dir);
    fill(first);

    JsonStorage second = JsonStorage.open(dir);
    JsonCodec codec = new JsonCodec();
    // сутності порівнюються за id, тому вміст звіряємо через JSON
    assertThat(codec.write(second.buses().findAll())).isEqualTo(codec.write(first.buses().findAll()));
    assertThat(codec.write(second.trips().findAll())).isEqualTo(codec.write(first.trips().findAll()));
    assertThat(codec.write(second.tickets().findAll()))
        .isEqualTo(codec.write(first.tickets().findAll()));
    assertThat(second.cities().findAll()).containsExactly(KYIV, ZHYTOMYR, RIVNE);
    assertThat(second.routes().getById(12)).isEqualTo(ROUTE);
    assertThat(second.tariffs().count()).isEqualTo(2L);
    assertThat(second.tariffs().getById(new TariffKey("KYIV", "RIVNE", null)).fare())
        .isEqualByComparingTo("450");
    assertThat(second.scheduleTemplates().getById("R12").expectedPassengers()).isEqualTo(40);
    assertThat(second.luggage().getById("S1")).isInstanceOf(Shipment.class);
    assertThat(second.luggage().getById("L1")).isInstanceOf(PassengerLuggage.class);
  }

  @Test
  void tripsAreLinkedToRoutesFromCatalogue() {
    fill(JsonStorage.open(dir));
    JsonStorage storage = JsonStorage.open(dir);

    Trip trip = storage.trips().getById("T1");
    assertThat(trip.getRoute()).isSameAs(storage.routes().getById(12));
    assertThat(trip.getBusId()).isEqualTo("AA1234");
    assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED);
    assertThat(trip.arrival()).isEqualTo(LocalDateTime.of(2026, 9, 23, 12, 40));
    assertThat(storage.trips().getById("T2").hasBus()).isFalse();
  }

  @Test
  void ticketKeepsStatusAndPassenger() throws Exception {
    fill(JsonStorage.open(dir));
    Ticket ticket = JsonStorage.open(dir).tickets().getById("TK-1");

    assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    assertThat(ticket.getPassenger().phone()).isEqualTo("+380501112233");
    assertThat(Files.readString(dir.resolve(JsonStorage.TICKETS))).doesNotContain("active");
  }

  @Test
  void tripWithUnknownRouteFails() throws Exception {
    fill(JsonStorage.open(dir));
    Files.writeString(dir.resolve(JsonStorage.ROUTES), "[]");

    assertThatThrownBy(() -> JsonStorage.open(dir))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("unknown route 12");
  }

  @Test
  void emptyDirectoryOpensEmpty() {
    JsonStorage storage = JsonStorage.open(dir.resolve("fresh"));
    assertThat(storage.cities().count()).isEqualTo(0L);
    assertThat(storage.trips().findAll()).isEmpty();
  }
}
