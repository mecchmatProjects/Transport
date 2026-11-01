package transport.exception;

public class DuplicateEntityException extends TransportException {

  private static final long serialVersionUID = 1L;

  public DuplicateEntityException(String entityType, Object id) {
    super(entityType + " already exists: " + id);
  }
}
