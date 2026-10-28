package transport.exception;

/** Автобус призначено на рейси, що перетинаються в часі (з урахуванням часу на розворот). */
public class ScheduleConflictException extends TransportException {

  private static final long serialVersionUID = 1L;

  public ScheduleConflictException(String message) {
    super(message);
  }
}
