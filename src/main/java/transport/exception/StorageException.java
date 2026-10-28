package transport.exception;

/**
 * Помилка сховища: файл не читається або не пишеться, JSON битий, посилання на неіснуючу сутність
 * (напр. маршрут посилається на місто, якого немає в каталозі). Оригінальна причина — у {@link
 * #getCause()}.
 */
public class StorageException extends TransportException {

  private static final long serialVersionUID = 1L;

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
