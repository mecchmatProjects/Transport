package transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import transport.exception.EntityNotFoundException;
import transport.exception.InvalidStatusTransitionException;
import transport.exception.LuggageCapacityExceededException;
import transport.exception.ValidationException;
import transport.model.Bus;
import transport.model.Contact;
import transport.model.Luggage;
import transport.model.LuggageStatus;
import transport.model.Passenger;
import transport.model.PassengerLuggage;
import transport.model.Route;
import transport.model.Shipment;
import transport.service.tariff.TariffPolicy;

class LuggageServiceTest {

  private static final Passenger IVAN = new Passenger("P1", "Ivan Petrenko", "+380501112233");
  private static final Contact SENDER = new Contact("Olena Shevchuk", null);
  private static final Contact RECIPIENT = new Contact("Taras Bondar", "+380671234567");
  private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

  private final ServiceFixture f = new ServiceFixture();
  private final TariffPolicy pricing = new PerKgPerSegmentPrice();
  private final TicketService tickets = new TicketService(f.tickets, f.trips, f.buses, pricing,
      Clock.fixed(NOW, ZoneOffset.UTC));
  private final LuggageService service =
      new LuggageService(f.luggage, f.tickets, f.trips, f.buses, pricing);

  // Рейс T1 по маршруту 12 (Kyiv, Zhytomyr, Rivne, Lutsk), автобус на 100 кг багажу
  @BeforeEach
  void setUp() {
    f.buses.add(new Bus("MINI", "Sprinter", 2, 100, "KYIV"));
    f.tripService.create(f.trip("T1", LocalTime.of(8, 0)));
    f.tripService.assignBus("T1", "MINI");
  }

  @Test
  void passengerLuggageTakesLegAndPriceFromTicket() {
    tickets.sell("T1", IVAN, "KYIV", "RIVNE");

    PassengerLuggage item = service.registerPassengerLuggage("T1-001", 18, 0.1);

    assertThat(item.getId()).isEqualTo("T1-L001");
    assertThat(item.getTicketNumber()).isEqualTo("T1-001");
    assertThat(item.getFromCityId()).isEqualTo("KYIV");
    assertThat(item.getToCityId()).isEqualTo("RIVNE");
    assertThat(item.getPrice()).isEqualByComparingTo("72");
    assertThat(item.getStatus()).isEqualTo(LuggageStatus.REGISTERED);
    assertThat(service.get("T1-L001")).isEqualTo(item);
  }

  @Test
  void shipmentIsRegisteredWithoutTicket() {
    Shipment item = service.registerShipment("T1", "KYIV", "LUTSK", 18, 0.2, SENDER, RECIPIENT);

    assertThat(item.getId()).isEqualTo("T1-S001");
    assertThat(item.getPrice()).isEqualByComparingTo("108");
    assertThat(item.getSender()).isEqualTo(SENDER);
    assertThat(item.getRecipient()).isEqualTo(RECIPIENT);
    assertThat(item.getStatus()).isEqualTo(LuggageStatus.REGISTERED);
  }

  // Випадок 14
  @Test
  void acceptsLastKilogramOnSegment() {
    shipment("RIVNE", "LUTSK", 99);
    assertThat(service.availableKg("T1", "RIVNE", "LUTSK")).isEqualTo(1);

    shipment("RIVNE", "LUTSK", 1);

    assertThat(service.availableKg("T1", "RIVNE", "LUTSK")).isZero();
    assertThat(service.loadBySegment("T1")).containsExactly(0.0, 0.0, 100.0);
  }

  // Випадок 15: переповнена лише остання ділянка, але відрізок її зачіпає
  @Test
  void checksEverySegmentOfTheLeg() {
    shipment("RIVNE", "LUTSK", 100);

    assertThat(service.availableKg("T1", "KYIV", "RIVNE")).isEqualTo(100);
    assertThat(service.availableKg("T1", "KYIV", "LUTSK")).isZero();
    assertThatThrownBy(() -> shipment("KYIV", "LUTSK", 1))
        .isInstanceOfSatisfying(LuggageCapacityExceededException.class, e -> {
          assertThat(e.getSegmentIndex()).isEqualTo(2);
          assertThat(e.getRequestedKg()).isEqualTo(1);
          assertThat(e.getAvailableKg()).isZero();
        });
    assertThat(f.luggage.count()).isEqualTo(1);
    shipment("KYIV", "RIVNE", 100);
  }

  @Test
  void rejectsAnyExcessOverCapacity() {
    shipment("KYIV", "ZHYTOMYR", 99.5);

    assertThatThrownBy(() -> shipment("KYIV", "ZHYTOMYR", 0.6))
        .isInstanceOf(LuggageCapacityExceededException.class);
    shipment("KYIV", "ZHYTOMYR", 0.5);
    assertThat(service.availableKg("T1", "KYIV", "ZHYTOMYR")).isZero();
  }

  // 0.1 + 49.9 + 49.9 + 0.1 у double дає хибну відмову без похибки порівняння
  @Test
  void fractionalKilogramsDoNotCauseFalseRefusal() {
    shipment("KYIV", "ZHYTOMYR", 0.1);
    shipment("KYIV", "ZHYTOMYR", 49.9);
    shipment("KYIV", "ZHYTOMYR", 49.9);

    shipment("KYIV", "ZHYTOMYR", 0.1);

    assertThatThrownBy(() -> shipment("KYIV", "ZHYTOMYR", 0.01))
        .isInstanceOf(LuggageCapacityExceededException.class);
  }

  @Test
  void luggageOnDisjointLegsDoesNotAddUp() {
    shipment("KYIV", "ZHYTOMYR", 100);
    shipment("ZHYTOMYR", "LUTSK", 100);

    assertThat(service.loadBySegment("T1")).containsExactly(100.0, 100.0, 100.0);
  }

  @Test
  void passengerLuggageCountsTowardsSameCapacity() {
    tickets.sell("T1", IVAN, "KYIV", "LUTSK");
    service.registerPassengerLuggage("T1-001", 60, 0.2);

    assertThatThrownBy(() -> shipment("ZHYTOMYR", "RIVNE", 41))
        .isInstanceOf(LuggageCapacityExceededException.class);
    shipment("ZHYTOMYR", "RIVNE", 40);
  }

  @Test
  void rejectsNonPositiveWeightAndBadVolume() {
    assertThatThrownBy(() -> shipment("KYIV", "RIVNE", -5))
        .isInstanceOf(ValidationException.class).hasMessageContaining("weight");
    assertThatThrownBy(() -> shipment("KYIV", "RIVNE", 0))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> shipment("KYIV", "RIVNE", Double.NaN))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> shipment("KYIV", "RIVNE", Double.POSITIVE_INFINITY))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(
        () -> service.registerShipment("T1", "KYIV", "RIVNE", 5, -0.1, SENDER, RECIPIENT))
        .isInstanceOf(ValidationException.class).hasMessageContaining("volume");
    assertThat(f.luggage.count()).isZero();
  }

  // Випадок 16
  @Test
  void rejectsWrongDirection() {
    assertThatThrownBy(() -> shipment("RIVNE", "RIVNE", 5))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> shipment("LUTSK", "KYIV", 5))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("does not go");
    assertThatThrownBy(() -> shipment("KYIV", "UMAN", 5))
        .isInstanceOf(ValidationException.class);
    assertThat(f.luggage.count()).isZero();
  }

  // Випадок 17: повторне скасування це помилка, місце звільняється після першого
  @Test
  void cancelFreesCapacityAndSecondCancelFails() {
    shipment("KYIV", "LUTSK", 100);

    Luggage cancelled = service.cancel("T1-S001");

    assertThat(cancelled.getStatus()).isEqualTo(LuggageStatus.CANCELLED);
    assertThat(service.get("T1-S001").getStatus()).isEqualTo(LuggageStatus.CANCELLED);
    assertThatThrownBy(() -> service.cancel("T1-S001"))
        .isInstanceOf(InvalidStatusTransitionException.class);
    assertThat(service.availableKg("T1", "KYIV", "LUTSK")).isEqualTo(100);
    shipment("KYIV", "LUTSK", 100);
  }

  @Test
  void lifecycleGoesThroughEveryStatus() {
    shipment("KYIV", "LUTSK", 10);

    assertThat(service.load("T1-S001").getStatus()).isEqualTo(LuggageStatus.LOADED);
    assertThat(service.depart("T1-S001").getStatus()).isEqualTo(LuggageStatus.IN_TRANSIT);
    assertThat(service.arrive("T1-S001").getStatus()).isEqualTo(LuggageStatus.ARRIVED);
    assertThat(service.deliver("T1-S001").getStatus()).isEqualTo(LuggageStatus.DELIVERED);
    assertThat(service.get("T1-S001").getStatus()).isEqualTo(LuggageStatus.DELIVERED);
  }

  @Test
  void rejectsStepsOutOfOrder() {
    shipment("KYIV", "LUTSK", 10);

    assertThatThrownBy(() -> service.deliver("T1-S001"))
        .isInstanceOf(InvalidStatusTransitionException.class);
    assertThatThrownBy(() -> service.depart("T1-S001"))
        .isInstanceOf(InvalidStatusTransitionException.class);
    service.load("T1-S001");
    service.depart("T1-S001");
    assertThatThrownBy(() -> service.cancel("T1-S001"))
        .isInstanceOf(InvalidStatusTransitionException.class);
    assertThat(service.get("T1-S001").getStatus()).isEqualTo(LuggageStatus.IN_TRANSIT);
  }

  @Test
  void arrivedAndDeliveredLuggageFreesCapacity() {
    shipment("KYIV", "LUTSK", 100);
    service.load("T1-S001");
    service.depart("T1-S001");
    assertThat(service.availableKg("T1", "KYIV", "LUTSK")).isZero();

    service.arrive("T1-S001");

    assertThat(service.availableKg("T1", "KYIV", "LUTSK")).isEqualTo(100);
    assertThat(service.loadBySegment("T1")).containsExactly(0.0, 0.0, 0.0);
  }

  @Test
  void passengerLuggageNeedsSoldTicket() {
    tickets.sell("T1", IVAN, "KYIV", "RIVNE");
    tickets.cancel("T1-001");

    assertThatThrownBy(() -> service.registerPassengerLuggage("T1-001", 10, 0.1))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("CANCELLED");
    assertThatThrownBy(() -> service.registerPassengerLuggage("T1-404", 10, 0.1))
        .isInstanceOf(EntityNotFoundException.class);
    assertThat(f.luggage.count()).isZero();
  }

  @Test
  void registersOnlyForScheduledTripWithBus() {
    f.tripService.create(f.trip("T2", LocalTime.of(16, 0)));
    assertThatThrownBy(() -> service.registerShipment("T2", "KYIV", "RIVNE", 5, 0.1, SENDER,
        RECIPIENT))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("no bus");

    f.tripService.cancel("T1");
    assertThatThrownBy(() -> shipment("KYIV", "RIVNE", 5))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("CANCELLED");

    assertThatThrownBy(() -> service.registerShipment("T404", "KYIV", "RIVNE", 5, 0.1, SENDER,
        RECIPIENT))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void refusesWhenTariffIsUnknown() {
    LuggageService noPrice = new LuggageService(f.luggage, f.tickets, f.trips, f.buses,
        new NoLuggagePrice());

    assertThatThrownBy(() -> noPrice.registerShipment("T1", "KYIV", "RIVNE", 5, 0.1, SENDER,
        RECIPIENT))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("no tariff");
    assertThat(f.luggage.count()).isZero();
  }

  @Test
  void loadBySegmentCountsOnlyLuggageThatOccupiesCapacity() {
    shipment("KYIV", "RIVNE", 10);
    shipment("ZHYTOMYR", "LUTSK", 20);
    shipment("RIVNE", "LUTSK", 30);
    service.cancel("T1-S003");

    assertThat(service.loadBySegment("T1")).containsExactly(10.0, 30.0, 20.0);
  }

  // Багаж із JSON з містом поза маршрутом не валить реєстрацію, але займає місце на весь рейс
  @Test
  void brokenRecordDoesNotBreakRegistrationAndKeepsItsWeight() {
    f.luggage.add(new Shipment("T1-S900", "T1", "KYIV", "UMAN", 60, 0.1, BigDecimal.TEN,
        SENDER, RECIPIENT));

    assertThat(service.loadBySegment("T1")).containsExactly(0.0, 0.0, 0.0);
    assertThat(service.availableKg("T1", "RIVNE", "LUTSK")).isEqualTo(40);
    assertThatThrownBy(() -> shipment("RIVNE", "LUTSK", 41))
        .isInstanceOf(LuggageCapacityExceededException.class);
    shipment("RIVNE", "LUTSK", 40);
  }

  @Test
  void idsAreNotReusedAfterCancel() {
    shipment("KYIV", "RIVNE", 5);
    service.cancel("T1-S001");

    assertThat(shipment("KYIV", "RIVNE", 5).getId()).isEqualTo("T1-S002");
  }

  @Test
  void cancelAllForTripSkipsLuggageThatCannotBeCancelled() {
    shipment("KYIV", "RIVNE", 5);
    shipment("KYIV", "RIVNE", 5);
    shipment("KYIV", "RIVNE", 5);
    shipment("KYIV", "RIVNE", 5);
    service.load("T1-S002");
    service.load("T1-S003");
    service.depart("T1-S003");
    service.cancel("T1-S004");

    assertThat(service.cancelAllForTrip("T1")).isEqualTo(2);
    assertThat(service.get("T1-S001").getStatus()).isEqualTo(LuggageStatus.CANCELLED);
    assertThat(service.get("T1-S002").getStatus()).isEqualTo(LuggageStatus.CANCELLED);
    assertThat(service.get("T1-S003").getStatus()).isEqualTo(LuggageStatus.IN_TRANSIT);
  }

  // Зауваження рев'ю PR #21: скасування квитка має звільняти місткість, зайняту його багажем
  @Test
  void cancelForTicketRestoresFullCapacity() {
    tickets.sell("T1", IVAN, "KYIV", "RIVNE");
    service.registerPassengerLuggage("T1-001", 90, 0.3);
    assertThat(service.availableKg("T1", "KYIV", "RIVNE")).isEqualTo(10);

    tickets.cancel("T1-001");
    assertThat(service.cancelForTicket("T1-001")).isEqualTo(1);

    assertThat(service.get("T1-L001").getStatus()).isEqualTo(LuggageStatus.CANCELLED);
    assertThat(service.availableKg("T1", "KYIV", "RIVNE")).isEqualTo(100);
    assertThat(service.loadBySegment("T1")).containsExactly(0.0, 0.0, 0.0);
    assertThatThrownBy(() -> service.load("T1-L001"))
        .isInstanceOf(InvalidStatusTransitionException.class);
  }

  @Test
  void cancelForTicketTouchesOnlyCancellableLuggageOfThatTicket() {
    tickets.sell("T1", IVAN, "KYIV", "RIVNE");
    tickets.sell("T1", IVAN, "KYIV", "RIVNE");
    service.registerPassengerLuggage("T1-001", 10, 0.1);
    service.registerPassengerLuggage("T1-001", 10, 0.1);
    service.registerPassengerLuggage("T1-001", 10, 0.1);
    service.registerPassengerLuggage("T1-002", 10, 0.1);
    shipment("KYIV", "RIVNE", 10);
    service.load("T1-L002");
    service.load("T1-L003");
    service.depart("T1-L003");

    assertThat(service.cancelForTicket("T1-001")).isEqualTo(2);

    assertThat(service.get("T1-L001").getStatus()).isEqualTo(LuggageStatus.CANCELLED);
    assertThat(service.get("T1-L002").getStatus()).isEqualTo(LuggageStatus.CANCELLED);
    assertThat(service.get("T1-L003").getStatus()).isEqualTo(LuggageStatus.IN_TRANSIT);
    assertThat(service.get("T1-L004").getStatus()).isEqualTo(LuggageStatus.REGISTERED);
    assertThat(service.get("T1-S001").getStatus()).isEqualTo(LuggageStatus.REGISTERED);
  }

  @Test
  void cancelForTicketIsIdempotentAndIgnoresTicketsWithoutLuggage() {
    tickets.sell("T1", IVAN, "KYIV", "RIVNE");
    service.registerPassengerLuggage("T1-001", 10, 0.1);

    assertThat(service.cancelForTicket("T1-001")).isEqualTo(1);
    assertThat(service.cancelForTicket("T1-001")).isZero();
    assertThat(service.cancelForTicket("T1-404")).isZero();
    assertThatThrownBy(() -> service.cancelForTicket(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void findByTripListsLuggageOfThatTripOnly() {
    f.tripService.create(f.trip("T2", LocalTime.of(16, 0)));
    f.tripService.assignBus("T2", "MINI");
    shipment("KYIV", "RIVNE", 5);
    service.registerShipment("T2", "KYIV", "RIVNE", 5, 0.1, SENDER, RECIPIENT);

    assertThat(service.findByTrip("T1")).extracting(Luggage::getId).containsExactly("T1-S001");
    assertThat(service.list()).hasSize(2);
  }

  @Test
  void returnsCopies() {
    Shipment item = shipment("KYIV", "RIVNE", 5);
    item.cancel();

    assertThat(service.get(item.getId()).getStatus()).isEqualTo(LuggageStatus.REGISTERED);
    assertThat(service.availableKg("T1", "KYIV", "RIVNE")).isEqualTo(95);
  }

  private Shipment shipment(String from, String to, double kg) {
    return service.registerShipment("T1", from, to, kg, 0.1, SENDER, RECIPIENT);
  }

  // 100 за кожну ділянку квитка, 2 за кілограм на ділянку багажу
  private static final class PerKgPerSegmentPrice implements TariffPolicy {

    @Override
    public Optional<BigDecimal> ticketPrice(Route route, String fromCityId, String toCityId) {
      int[] range = route.stopRange(fromCityId, toCityId);
      return Optional.of(BigDecimal.valueOf(100L * (range[1] - range[0])));
    }

    @Override
    public Optional<BigDecimal> luggagePrice(
        Route route, String fromCityId, String toCityId, double kg) {
      int[] range = route.stopRange(fromCityId, toCityId);
      return Optional.of(BigDecimal.valueOf(2 * kg * (range[1] - range[0])));
    }
  }

  private static final class NoLuggagePrice implements TariffPolicy {

    @Override
    public Optional<BigDecimal> ticketPrice(Route route, String fromCityId, String toCityId) {
      return Optional.empty();
    }

    @Override
    public Optional<BigDecimal> luggagePrice(
        Route route, String fromCityId, String toCityId, double kg) {
      return Optional.empty();
    }
  }
}
