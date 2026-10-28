package transport.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Пасажирський квиток між будь-якими двома зупинками рейсу (README §4). Відповідність зупинок
 * маршруту та вільні місця перевіряє {@code TicketService}.
 */
public class Ticket {

  private final String number;
  private final String tripId;
  private final Passenger passenger;
  private final String fromCityId;
  private final String toCityId;
  private final int seatNumber;
  private final BigDecimal price;
  private final LocalDateTime soldAt;
  private TicketStatus status;

  /** Щойно проданий квиток ({@link TicketStatus#SOLD}). */
  public Ticket(
      String number,
      String tripId,
      Passenger passenger,
      String fromCityId,
      String toCityId,
      int seatNumber,
      BigDecimal price,
      LocalDateTime soldAt) {
    this(number, tripId, passenger, fromCityId, toCityId, seatNumber, price, soldAt, TicketStatus.SOLD);
  }

  /** Повний конструктор (для завантаження зі сховища). */
  public Ticket(
      String number,
      String tripId,
      Passenger passenger,
      String fromCityId,
      String toCityId,
      int seatNumber,
      BigDecimal price,
      LocalDateTime soldAt,
      TicketStatus status) {
    this.number = Checks.requireText(number, "number");
    this.tripId = Checks.requireText(tripId, "tripId");
    this.passenger = Objects.requireNonNull(passenger, "passenger");
    this.fromCityId = Checks.requireText(fromCityId, "fromCityId");
    this.toCityId = Checks.requireText(toCityId, "toCityId");
    Checks.requireDifferent(fromCityId, toCityId);
    this.seatNumber = Checks.requirePositive(seatNumber, "seatNumber");
    this.price = Checks.requireNonNegative(price, "price");
    this.soldAt = Objects.requireNonNull(soldAt, "soldAt");
    this.status = Objects.requireNonNull(status, "status");
  }

  public String getNumber() {
    return number;
  }

  public String getTripId() {
    return tripId;
  }

  public Passenger getPassenger() {
    return passenger;
  }

  public String getFromCityId() {
    return fromCityId;
  }

  public String getToCityId() {
    return toCityId;
  }

  public int getSeatNumber() {
    return seatNumber;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public LocalDateTime getSoldAt() {
    return soldAt;
  }

  public TicketStatus getStatus() {
    return status;
  }

  /** Чи займає квиток місце (не скасований і не використаний). */
  public boolean isActive() {
    return status.occupiesSeat();
  }

  public void cancel() {
    status = Transitional.transition(status, TicketStatus.CANCELLED);
  }

  public void markUsed() {
    status = Transitional.transition(status, TicketStatus.USED);
  }

  /**
   * Чи займає квиток ділянку маршруту {@code segmentIndex}, якщо поїздка займає зупинки {@code
   * [fromIndex; toIndex)}. Індекси бере сервіс з {@link Route#stopRange}.
   */
  public static boolean coversSegment(int fromIndex, int toIndex, int segmentIndex) {
    return segmentIndex >= fromIndex && segmentIndex < toIndex;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof Ticket other && number.equals(other.number));
  }

  @Override
  public int hashCode() {
    return number.hashCode();
  }

  @Override
  public String toString() {
    return "Ticket[" + number + ", trip " + tripId + ", " + fromCityId + "->" + toCityId
        + ", seat " + seatNumber + ", " + price + ", " + status + "]";
  }
}
