package transport.model;

public record Passenger(String id, String fullName, String phone) {

  public Passenger {
    Checks.requireText(id, "id");
    Checks.requireText(fullName, "fullName");
  }

  public Contact toContact() {
    return new Contact(fullName, phone);
  }
}
