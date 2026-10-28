package transport.model;

/** Поточний стан автобуса (README §3). */
public enum BusStatus {
  AVAILABLE,
  IN_SERVICE,
  MAINTENANCE,
  UNAVAILABLE;

  /** Чи можна планувати автобус на рейси (не на ремонті й не списаний). */
  public boolean isOperational() {
    return this == AVAILABLE || this == IN_SERVICE;
  }
}
