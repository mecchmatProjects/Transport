package transport.model;

import java.math.BigDecimal;

/** Багаж пасажира — прив'язаний до квитка, їде на тому ж рейсі й ділянці. */
public final class PassengerLuggage extends Luggage {

  private final String ticketNumber;

  /** Щойно зареєстрований багаж. Рейс і міста беруться з квитка. */
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

  /** Повний конструктор (для завантаження зі сховища). */
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
