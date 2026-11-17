package transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import transport.exception.EntityNotFoundException;
import transport.exception.InvalidStatusTransitionException;
import transport.exception.NoSeatsAvailableException;
import transport.exception.ValidationException;
import transport.model.Bus;
import transport.model.Passenger;
import transport.model.Route;
import transport.model.Ticket;
import transport.model.TicketStatus;
import transport.service.tariff.TariffPolicy;

class TicketServiceTest {

  private static final Passenger IVAN = new Passenger("P1", "Ivan Petrenko", "+380501112233");
  private static final Passenger OLHA = new Passenger("P2", "Olha Koval", null);
  private static final Passenger PETRO = new Passenger("P3", "Petro Bondar", null);
  private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

  private final ServiceFixture f = new ServiceFixture();
  private final TicketService service = new TicketService(f.tickets, f.trips, f.buses,
      new PerSegmentPrice(), Clock.fixed(NOW, ZoneOffset.UTC));

  // Рейс T1 по маршруту 12 (Kyiv → Zhytomyr → Rivne → Lutsk) на автобусі з двома місцями
  @BeforeEach
  void setUp() {
    f.buses.add(new Bus("MINI", "Sprinter", 2, 200, "KYIV"));
    f.tripService.create(f.trip("T1", LocalTime.of(8, 0)));
    f.tripService.assignBus("T1", "MINI");
  }

  @Test
  void sellFillsTicketFields() {
    Ticket ticket = service.sell("T1", IVAN, "KYIV", "RIVNE");

    assertThat(ticket.getNumber()).isEqualTo("T1-001");
    assertThat(ticket.getSeatNumber()).isEqualTo(1);
    assertThat(ticket.getPrice()).isEqualByComparingTo("200");
    assertThat(ticket.getSoldAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 10, 0));
    assertThat(ticket.getStatus()).isEqualTo(TicketStatus.SOLD);
    assertThat(service.get("T1-001")).isEqualTo(ticket);
  }

  // Випадок 4
  @Test
  void sellsLastSeatOnSegment() {
    service.sell("T1", IVAN, "KYIV", "RIVNE");
    assertThat(service.availableSeats("T1", "KYIV", "RIVNE")).isEqualTo(1);

    Ticket last = service.sell("T1", OLHA, "KYIV", "RIVNE");

    assertThat(last.getSeatNumber()).isEqualTo(2);
    assertThat(service.availableSeats("T1", "KYIV", "RIVNE")).isZero();
  }

  // Випадок 5
  @Test
  void rejectsTicketWhenSegmentIsFull() {
    service.sell("T1", IVAN, "KYIV", "ZHYTOMYR");
    service.sell("T1", OLHA, "KYIV", "ZHYTOMYR");

    assertThatThrownBy(() -> service.sell("T1", PETRO, "KYIV", "ZHYTOMYR"))
        .isInstanceOf(NoSeatsAvailableException.class)
        .hasMessageContaining("KYIV -> ZHYTOMYR");
    assertThat(f.tickets.count()).isEqualTo(2);
  }

  // Випадок 6: переповнена лише остання ділянка, але квиток її зачіпає
  @Test
  void checksEverySegmentOfTheLeg() {
    service.sell("T1", IVAN, "RIVNE", "LUTSK");
    service.sell("T1", OLHA, "RIVNE", "LUTSK");

    assertThat(service.availableSeats("T1", "KYIV", "RIVNE")).isEqualTo(2);
    assertThatThrownBy(() -> service.sell("T1", PETRO, "KYIV", "LUTSK"))
        .isInstanceOf(NoSeatsAvailableException.class);
    assertThat(service.sell("T1", PETRO, "KYIV", "RIVNE").getSeatNumber()).isEqualTo(1);
  }

  @Test
  void seatIsReusedOnDisjointLegs() {
    service.sell("T1", IVAN, "KYIV", "ZHYTOMYR", 1);
    Ticket next = service.sell("T1", OLHA, "ZHYTOMYR", "LUTSK", 1);

    assertThat(next.getSeatNumber()).isEqualTo(1);
    assertThat(service.passengersBySegment("T1")).containsExactly(1, 1, 1);
  }

  @Test
  void rejectsTakenOrMissingSeat() {
    service.sell("T1", IVAN, "KYIV", "RIVNE", 2);

    assertThatThrownBy(() -> service.sell("T1", OLHA, "ZHYTOMYR", "LUTSK", 2))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("seat 2");
    assertThatThrownBy(() -> service.sell("T1", OLHA, "KYIV", "RIVNE", 3))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("no seat 3");
    assertThatThrownBy(() -> service.sell("T1", OLHA, "KYIV", "RIVNE", 0))
        .isInstanceOf(ValidationException.class);
  }

  // Випадок 7
  @Test
  void rejectsWrongDirection() {
    assertThatThrownBy(() -> service.sell("T1", IVAN, "RIVNE", "RIVNE"))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> service.sell("T1", IVAN, "LUTSK", "KYIV"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("does not go");
    assertThatThrownBy(() -> service.sell("T1", IVAN, "KYIV", "UMAN"))
        .isInstanceOf(ValidationException.class);
    assertThat(f.tickets.count()).isZero();
  }

  // Випадок 8: повторне скасування це помилка, місце звільняється після першого
  @Test
  void cancelFreesSeatAndSecondCancelFails() {
    service.sell("T1", IVAN, "KYIV", "LUTSK");
    service.sell("T1", OLHA, "KYIV", "LUTSK");

    Ticket cancelled = service.cancel("T1-001");

    assertThat(cancelled.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    assertThat(service.get("T1-001").getStatus()).isEqualTo(TicketStatus.CANCELLED);
    assertThatThrownBy(() -> service.cancel("T1-001"))
        .isInstanceOf(InvalidStatusTransitionException.class);
    assertThat(service.sell("T1", PETRO, "KYIV", "LUTSK").getSeatNumber()).isEqualTo(1);
  }

  @Test
  void ticketNumbersAreNotReusedAfterCancel() {
    service.sell("T1", IVAN, "KYIV", "RIVNE");
    service.cancel("T1-001");

    assertThat(service.sell("T1", OLHA, "KYIV", "RIVNE").getNumber()).isEqualTo("T1-002");
  }

  @Test
  void usedTicketCannotBeCancelled() {
    service.sell("T1", IVAN, "KYIV", "RIVNE");
    service.markUsed("T1-001");

    assertThatThrownBy(() -> service.cancel("T1-001"))
        .isInstanceOf(InvalidStatusTransitionException.class);
  }

  @Test
  void sellsOnlyForScheduledTripWithBus() {
    f.tripService.create(f.trip("T2", LocalTime.of(16, 0)));
    assertThatThrownBy(() -> service.sell("T2", IVAN, "KYIV", "RIVNE"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("no bus");

    f.tripService.cancel("T1");
    assertThatThrownBy(() -> service.sell("T1", IVAN, "KYIV", "RIVNE"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("CANCELLED");

    assertThatThrownBy(() -> service.sell("T404", IVAN, "KYIV", "RIVNE"))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void passengersBySegmentCountsOnlyActiveTickets() {
    service.sell("T1", IVAN, "KYIV", "RIVNE");
    service.sell("T1", OLHA, "ZHYTOMYR", "LUTSK");
    service.sell("T1", PETRO, "RIVNE", "LUTSK");
    service.cancel("T1-003");

    assertThat(service.passengersBySegment("T1")).containsExactly(1, 2, 1);
  }

  @Test
  void cancelAllForTripSkipsInactive() {
    service.sell("T1", IVAN, "KYIV", "RIVNE");
    service.sell("T1", OLHA, "KYIV", "RIVNE");
    service.cancel("T1-001");

    assertThat(service.cancelAllForTrip("T1")).isEqualTo(1);
    assertThat(service.findByTrip("T1")).allMatch(t -> t.getStatus() == TicketStatus.CANCELLED);
  }

  @Test
  void returnsCopies() {
    Ticket sold = service.sell("T1", IVAN, "KYIV", "RIVNE");
    sold.cancel();

    assertThat(service.get(sold.getNumber()).getStatus()).isEqualTo(TicketStatus.SOLD);
    assertThat(service.availableSeats("T1", "KYIV", "RIVNE")).isEqualTo(1);
  }

  // 100 за кожну ділянку, щоб ціна не залежала від тарифної таблиці
  private static final class PerSegmentPrice implements TariffPolicy {

    @Override
    public Optional<BigDecimal> ticketPrice(Route route, String fromCityId, String toCityId) {
      int[] range = route.stopRange(fromCityId, toCityId);
      return Optional.of(BigDecimal.valueOf(100L * (range[1] - range[0])));
    }

    @Override
    public Optional<BigDecimal> luggagePrice(
        Route route, String fromCityId, String toCityId, double kg) {
      return Optional.empty();
    }
  }
}
