package transport.exception;

/**
 * Перевищено місткість автобуса на якійсь ділянці маршруту. Абстрактний: конкретний випадок — це
 * {@link NoSeatsAvailableException} (місця) або {@link LuggageCapacityExceededException} (багаж).
 * Ловити базовий тип зручно, коли байдуже, що саме закінчилось.
 */
public abstract class CapacityExceededException extends TransportException {

  private static final long serialVersionUID = 1L;

  private final String tripId;

  protected CapacityExceededException(String tripId, String message) {
    super(message);
    this.tripId = tripId;
  }

  /** Рейс, на якому не вистачило місткості. */
  public String getTripId() {
    return tripId;
  }
}
