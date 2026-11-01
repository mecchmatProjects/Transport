package transport.model;

public enum LuggageStatus implements Transitional<LuggageStatus> {
  REGISTERED,
  LOADED,
  IN_TRANSIT,
  ARRIVED,
  DELIVERED,
  CANCELLED;

  @Override
  public boolean canTransitionTo(LuggageStatus next) {
    return switch (this) {
      case REGISTERED -> next == LOADED || next == CANCELLED;
      case LOADED -> next == IN_TRANSIT || next == CANCELLED;
      case IN_TRANSIT -> next == ARRIVED;
      case ARRIVED -> next == DELIVERED;
      case DELIVERED, CANCELLED -> false;
    };
  }

  public boolean occupiesCapacity() {
    return this == REGISTERED || this == LOADED || this == IN_TRANSIT;
  }
}
