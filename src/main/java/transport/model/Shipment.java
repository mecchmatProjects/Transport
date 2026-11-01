package transport.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class Shipment extends Luggage {

  private final Contact sender;
  private final Contact recipient;

  public Shipment(
      String id,
      String tripId,
      String fromCityId,
      String toCityId,
      double weightKg,
      double volumeM3,
      BigDecimal price,
      Contact sender,
      Contact recipient) {
    this(id, tripId, fromCityId, toCityId, weightKg, volumeM3, price, LuggageStatus.REGISTERED,
        sender, recipient);
  }

  public Shipment(
      String id,
      String tripId,
      String fromCityId,
      String toCityId,
      double weightKg,
      double volumeM3,
      BigDecimal price,
      LuggageStatus status,
      Contact sender,
      Contact recipient) {
    super(id, tripId, fromCityId, toCityId, weightKg, volumeM3, price, status);
    this.sender = Objects.requireNonNull(sender, "sender");
    this.recipient = Objects.requireNonNull(recipient, "recipient");
  }

  public Contact getSender() {
    return sender;
  }

  public Contact getRecipient() {
    return recipient;
  }

  @Override
  public String description() {
    return "Shipment " + getFromCityId() + " -> " + getToCityId() + " from " + sender.fullName()
        + " to " + recipient.fullName() + ", " + getWeightKg() + " kg";
  }
}
