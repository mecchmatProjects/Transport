package transport.exception;

/** Базовий виняток предметної області. Усі бізнес-помилки системи успадковуються від нього. */
public class TransportException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TransportException(String message) {
    super(message);
  }

  public TransportException(String message, Throwable cause) {
    super(message, cause);
  }
}
