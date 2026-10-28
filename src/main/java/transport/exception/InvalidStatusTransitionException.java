package transport.exception;

/** Недопустимий перехід між статусами (напр. квиток {@code CANCELLED -> USED}). */
public class InvalidStatusTransitionException extends TransportException {

  private static final long serialVersionUID = 1L;

  /**
   * @param from поточний статус
   * @param to статус, у який намагались перейти
   */
  public InvalidStatusTransitionException(Enum<?> from, Enum<?> to) {
    super("Invalid " + from.getDeclaringClass().getSimpleName() + " transition: " + from + " -> " + to);
  }
}
