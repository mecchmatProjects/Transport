package transport.exception;

public class InvalidStatusTransitionException extends TransportException {

  private static final long serialVersionUID = 1L;

  public InvalidStatusTransitionException(Enum<?> from, Enum<?> to) {
    super("Invalid " + from.getDeclaringClass().getSimpleName() + " transition: " + from + " -> " + to);
  }
}
