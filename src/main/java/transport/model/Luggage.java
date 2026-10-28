package transport.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Багаж на рейсі (README §6). Два варіанти — {@link PassengerLuggage} (прив'язаний до квитка) та
 * {@link Shipment} (окреме відправлення без пасажира). Спільні дані й життєвий цикл — тут.
 */
public abstract sealed class Luggage permits PassengerLuggage, Shipment {

  private final String id;
  private final String tripId;
  private final String fromCityId;
  private final String toCityId;
  private final double weightKg;
  private final double volumeM3;
  private final BigDecimal price;
  private LuggageStatus status;

  protected Luggage(
      String id,
      String tripId,
      String fromCityId,
      String toCityId,
      double weightKg,
      double volumeM3,
      BigDecimal price,
      LuggageStatus status) {
    this.id = Checks.requireText(id, "id");
    this.tripId = Checks.requireText(tripId, "tripId");
    this.fromCityId = Checks.requireText(fromCityId, "fromCityId");
    this.toCityId = Checks.requireText(toCityId, "toCityId");
    Checks.requireDifferent(fromCityId, toCityId);
    this.weightKg = Checks.requirePositive(weightKg, "weightKg");
    this.volumeM3 = Checks.requireNonNegative(volumeM3, "volumeM3");
    this.price = Checks.requireNonNegative(price, "price");
    this.status = Objects.requireNonNull(status, "status");
  }

  /** Короткий опис для звітів і CLI. */
  public abstract String description();

  public String getId() {
    return id;
  }

  public String getTripId() {
    return tripId;
  }

  public String getFromCityId() {
    return fromCityId;
  }

  public String getToCityId() {
    return toCityId;
  }

  public double getWeightKg() {
    return weightKg;
  }

  public double getVolumeM3() {
    return volumeM3;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public LuggageStatus getStatus() {
    return status;
  }

  /** Чи враховується вага в завантаженні автобуса. */
  public boolean occupiesCapacity() {
    return status.occupiesCapacity();
  }

  /** Перехід за правилами {@link LuggageStatus}. */
  public void changeStatus(LuggageStatus next) {
    status = Transitional.transition(status, next);
  }

  public void load() {
    changeStatus(LuggageStatus.LOADED);
  }

  public void depart() {
    changeStatus(LuggageStatus.IN_TRANSIT);
  }

  public void arrive() {
    changeStatus(LuggageStatus.ARRIVED);
  }

  public void deliver() {
    changeStatus(LuggageStatus.DELIVERED);
  }

  public void cancel() {
    changeStatus(LuggageStatus.CANCELLED);
  }

  @Override
  public final boolean equals(Object o) {
    return this == o || (o instanceof Luggage other && id.equals(other.id));
  }

  @Override
  public final int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + id + ", trip " + tripId + ", " + fromCityId + "->"
        + toCityId + ", " + weightKg + "kg, " + status + "]";
  }
}
