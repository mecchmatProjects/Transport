package transport.exception;

public class ValidationException extends TransportException {

  private static final long serialVersionUID = 1L;

  public ValidationException(String message) {
    super(message);
  }
}
