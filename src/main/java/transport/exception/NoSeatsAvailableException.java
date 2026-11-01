package transport.exception;

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
