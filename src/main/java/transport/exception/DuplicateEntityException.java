package transport.exception;

/** Спроба додати сутність з ідентифікатором, який уже існує. */
public class DuplicateEntityException extends TransportException {

  private static final long serialVersionUID = 1L;

  /**
   * @param entityType назва типу, напр. {@code "City"}
   * @param id ідентифікатор, що повторюється
   */
  public DuplicateEntityException(String entityType, Object id) {
    super(entityType + " already exists: " + id);
  }
}
