package transport.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Автобус (README §3). Ідентичність — за {@link #getId()} (держномер).
 *
 * <p>Доступність у заданий період = робочий статус + відсутність перетину з періодами
 * недоступності (ТО, відпустка водія тощо).
 */
public class Bus {

  private final String id;
  private final String model;
  private final int seats;
  private final double luggageCapacityKg;
  private BusStatus status;
  private final List<TimeWindow> unavailablePeriods;

  /** Новий автобус у статусі {@link BusStatus#AVAILABLE}. */
  public Bus(String id, String model, int seats, double luggageCapacityKg) {
    this(id, model, seats, luggageCapacityKg, BusStatus.AVAILABLE, List.of());
  }

  /** Повний конструктор (для завантаження зі сховища). */
  public Bus(
      String id,
      String model,
      int seats,
      double luggageCapacityKg,
      BusStatus status,
      Collection<TimeWindow> unavailablePeriods) {
    this.id = Checks.requireText(id, "id");
    this.model = Checks.requireText(model, "model");
    this.seats = Checks.requirePositive(seats, "seats");
    this.luggageCapacityKg = Checks.requireNonNegative(luggageCapacityKg, "luggageCapacityKg");
    this.status = Objects.requireNonNull(status, "status");
    this.unavailablePeriods = new ArrayList<>(Objects.requireNonNull(unavailablePeriods));
  }

  public String getId() {
    return id;
  }

  public String getModel() {
    return model;
  }

  public int getSeats() {
    return seats;
  }

  public double getLuggageCapacityKg() {
    return luggageCapacityKg;
  }

  public BusStatus getStatus() {
    return status;
  }

  public void setStatus(BusStatus status) {
    this.status = Objects.requireNonNull(status, "status");
  }

  public List<TimeWindow> getUnavailablePeriods() {
    return List.copyOf(unavailablePeriods);
  }

  public void addUnavailablePeriod(TimeWindow period) {
    unavailablePeriods.add(Objects.requireNonNull(period, "period"));
  }

  public boolean removeUnavailablePeriod(TimeWindow period) {
    return unavailablePeriods.remove(period);
  }

  /** Чи можна використати автобус протягом усього вікна {@code window}. */
  public boolean isAvailableFor(TimeWindow window) {
    return status.isOperational()
        && unavailablePeriods.stream().noneMatch(period -> period.overlaps(window));
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof Bus other && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Bus[" + id + ", " + model + ", seats=" + seats + ", luggage=" + luggageCapacityKg
        + "kg, " + status + "]";
  }
}
