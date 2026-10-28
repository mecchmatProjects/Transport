package transport.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import transport.exception.InvalidStatusTransitionException;

class TicketAndLuggageTest {

  private static final Passenger PASSENGER = new Passenger("P1", "Ivan Petrenko", "+380501112233");

  private static Ticket ticket() {
    return new Ticket(
        "TK-1",
        "T1",
        PASSENGER,
        "KYIV",
        "RIVNE",
        5,
        new BigDecimal("450"),
        LocalDateTime.of(2026, 9, 20, 12, 0));
  }

  @Test
  void ticketLifecycle() {
    Ticket ticket = ticket();
    assertThat(ticket.isActive()).isTrue();
    ticket.cancel();
    assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    assertThat(ticket.isActive()).isFalse();
    assertThatThrownBy(ticket::markUsed).isInstanceOf(InvalidStatusTransitionException.class);
  }

  @Test
  void ticketValidation() {
    assertThatThrownBy(
            () ->
                new Ticket("TK-2", "T1", PASSENGER, "KYIV", "KYIV", 1, BigDecimal.ONE,
                    LocalDateTime.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Ticket("TK-3", "T1", PASSENGER, "KYIV", "RIVNE", 0, BigDecimal.ONE,
                    LocalDateTime.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ticketCoversSegments() {
    // KYIV(0) -> RIVNE(2) займає ділянки 0 і 1
    assertThat(Ticket.coversSegment(0, 2, 0)).isTrue();
    assertThat(Ticket.coversSegment(0, 2, 1)).isTrue();
    assertThat(Ticket.coversSegment(0, 2, 2)).isFalse();
  }

  @Test
  void passengerLuggageTakesTripAndCitiesFromTicket() {
    PassengerLuggage luggage =
        new PassengerLuggage("L1", ticket(), 18, 0.1, new BigDecimal("90"));
    assertThat(luggage.getTripId()).isEqualTo("T1");
    assertThat(luggage.getFromCityId()).isEqualTo("KYIV");
    assertThat(luggage.getToCityId()).isEqualTo("RIVNE");
    assertThat(luggage.getTicketNumber()).isEqualTo("TK-1");
    assertThat(luggage.getStatus()).isEqualTo(LuggageStatus.REGISTERED);
  }

  @Test
  void shipmentFullLifecycle() {
    Luggage shipment =
        new Shipment("S1", "T1", "KYIV", "LUTSK", 18, 0.2, new BigDecimal("320"),
            new Contact("Olena", null), new Contact("Taras", "+380671234567"));
    assertThat(shipment.occupiesCapacity()).isTrue();
    shipment.load();
    shipment.depart();
    shipment.arrive();
    assertThat(shipment.occupiesCapacity()).isFalse();
    shipment.deliver();
    assertThat(shipment.getStatus()).isEqualTo(LuggageStatus.DELIVERED);
    assertThat(shipment.description()).contains("Olena");
  }

  @Test
  void luggageCannotBeCancelledInTransit() {
    Luggage shipment =
        new Shipment("S1", "T1", "KYIV", "LUTSK", 18, 0.2, BigDecimal.TEN,
            new Contact("Olena", null), new Contact("Taras", null));
    shipment.load();
    shipment.depart();
    assertThatThrownBy(shipment::cancel).isInstanceOf(InvalidStatusTransitionException.class);
  }

  @Test
  void terminalStatuses() {
    assertThat(LuggageStatus.DELIVERED.isTerminal()).isTrue();
    assertThat(LuggageStatus.LOADED.isTerminal()).isFalse();
    assertThat(TicketStatus.USED.isTerminal()).isTrue();
    assertThat(TripStatus.SCHEDULED.isTerminal()).isFalse();
  }
}
