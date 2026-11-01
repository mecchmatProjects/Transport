package transport.model;

import java.math.BigDecimal;

public final class PassengerLuggage extends Luggage {

  private final String ticketNumber;

  public PassengerLuggage(
      String id, Ticket ticket, double weightKg, double volumeM3, BigDecimal price) {
    this(
        id,
        ticket.getTripId(),
        ticket.getFromCityId(),
        ticket.getToCityId(),
        weightKg,
        volumeM3,
        price,
        LuggageStatus.REGISTERED,
        ticket.getNumber());
  }

  public PassengerLuggage(
      String id,
      String tripId,
      String fromCityId,
      String toCityId,
      double weightKg,
      double volumeM3,
      BigDecimal price,
      LuggageStatus status,
      String ticketNumber) {
    super(id, tripId, fromCityId, toCityId, weightKg, volumeM3, price, status);
    this.ticketNumber = Checks.requireText(ticketNumber, "ticketNumber");
  }

  public String getTicketNumber() {
    return ticketNumber;
  }

  @Override
  public String description() {
    return "Passenger luggage for ticket " + ticketNumber + ", " + getWeightKg() + " kg";
  }
}
