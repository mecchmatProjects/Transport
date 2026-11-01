package transport.exception;

public abstract class CapacityExceededException extends TransportException {

  private static final long serialVersionUID = 1L;

  private final String tripId;

  protected CapacityExceededException(String tripId, String message) {
    super(message);
    this.tripId = tripId;
  }

  public String getTripId() {
    return tripId;
  }
}
