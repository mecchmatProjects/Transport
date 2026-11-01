package transport.exception;

public class EntityNotFoundException extends TransportException {

  private static final long serialVersionUID = 1L;

  public EntityNotFoundException(String entityType, Object id) {
    super(entityType + " not found: " + id);
  }
}
