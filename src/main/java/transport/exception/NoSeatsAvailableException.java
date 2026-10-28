package transport.exception;

/**
 * На рейсі немає вільного місця на всьому відрізку {@code from → to}: хоча б на одній ділянці
 * всередині відрізка зайняті всі місця (README §7).
 */
public class NoSeatsAvailableException extends CapacityExceededException {

  private static final long serialVersionUID = 1L;

  private final String fromCityId;
  private final String toCityId;

  public NoSeatsAvailableException(String tripId, String fromCityId, String toCityId) {
    super(tripId, "No seats on trip " + tripId + " for " + fromCityId + " -> " + toCityId);
    this.fromCityId = fromCityId;
    this.toCityId = toCityId;
  }

  public String getFromCityId() {
    return fromCityId;
  }

  public String getToCityId() {
    return toCityId;
  }
}
