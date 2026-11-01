package transport.model;

public record Contact(String fullName, String phone) {

  public Contact {
    Checks.requireText(fullName, "fullName");
  }
}
