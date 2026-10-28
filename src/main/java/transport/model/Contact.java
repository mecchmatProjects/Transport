package transport.model;

/**
 * Контактна особа — відправник або одержувач окремого багажного відправлення.
 *
 * @param fullName ПІБ
 * @param phone телефон, може бути {@code null}
 */
public record Contact(String fullName, String phone) {

  public Contact {
    Checks.requireText(fullName, "fullName");
  }
}
