package transport.exception;

public class ScheduleConflictException extends TransportException {

  private static final long serialVersionUID = 1L;

  public ScheduleConflictException(String message) {
    super(message);
  }
}
