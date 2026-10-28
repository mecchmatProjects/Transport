package transport.model;

/**
 * Пасажир.
 *
 * @param id унікальний ідентифікатор
 * @param fullName ПІБ
 * @param phone телефон, може бути {@code null}
 */
public record Passenger(String id, String fullName, String phone) {

  public Passenger {
    Checks.requireText(id, "id");
    Checks.requireText(fullName, "fullName");
  }

  /** Пасажир як контакт (напр. для багажу). */
  public Contact toContact() {
    return new Contact(fullName, phone);
  }
}
