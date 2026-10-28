package transport.model;

/** Статус рейсу: {@code SCHEDULED -> IN_PROGRESS -> COMPLETED}, скасувати можна лише до відправлення. */
public enum TripStatus implements Transitional<TripStatus> {
  SCHEDULED,
  IN_PROGRESS,
  COMPLETED,
  CANCELLED;

  @Override
  public boolean canTransitionTo(TripStatus next) {
    return switch (this) {
      case SCHEDULED -> next == IN_PROGRESS || next == CANCELLED;
      case IN_PROGRESS -> next == COMPLETED;
      case COMPLETED, CANCELLED -> false;
    };
  }
}
