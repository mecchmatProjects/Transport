package transport.model;

public enum BusStatus {
  AVAILABLE,
  IN_SERVICE,
  MAINTENANCE,
  UNAVAILABLE;

  public boolean isOperational() {
    return this == AVAILABLE || this == IN_SERVICE;
  }
}
