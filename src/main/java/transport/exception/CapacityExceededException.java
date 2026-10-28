package transport.exception;

/** Перевищено пасажирську або багажну місткість автобуса на якійсь ділянці маршруту. */
public class CapacityExceededException extends TransportException {

  private static final long serialVersionUID = 1L;

  public CapacityExceededException(String message) {
    super(message);
  }
}
