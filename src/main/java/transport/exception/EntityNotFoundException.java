package transport.exception;

/** Сутність із заданим ідентифікатором не знайдено. */
public class EntityNotFoundException extends TransportException {

  private static final long serialVersionUID = 1L;

  /**
   * @param entityType назва типу, напр. {@code "Bus"}
   * @param id ідентифікатор, який шукали
   */
  public EntityNotFoundException(String entityType, Object id) {
    super(entityType + " not found: " + id);
  }
}
