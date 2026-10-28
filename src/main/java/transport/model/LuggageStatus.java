package transport.model;

/**
 * Життєвий цикл багажу (README §6):
 *
 * <pre>
 * REGISTERED -> LOADED -> IN_TRANSIT -> ARRIVED -> DELIVERED
 *      \           \
 *       +-----------+--> CANCELLED
 * </pre>
 */
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

  /** Чи займає багаж місткість автобуса (ще не вивантажений і не скасований). */
  public boolean occupiesCapacity() {
    return this == REGISTERED || this == LOADED || this == IN_TRANSIT;
  }
}
