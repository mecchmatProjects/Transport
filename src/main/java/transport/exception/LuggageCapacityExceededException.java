package transport.exception;

/**
 * Багаж не вміщується: на ділянці {@code segmentIndex} не виконується {@code currentLuggage +
 * newLuggage <= capacity} (README §7).
 */
public class LuggageCapacityExceededException extends CapacityExceededException {

  private static final long serialVersionUID = 1L;

  private final int segmentIndex;
  private final double requestedKg;
  private final double availableKg;

  /**
   * @param segmentIndex перша ділянка, де не вистачило місця
   * @param requestedKg вага нового багажу
   * @param availableKg скільки кг ще вільно на цій ділянці
   */
  public LuggageCapacityExceededException(
      String tripId, int segmentIndex, double requestedKg, double availableKg) {
    super(
        tripId,
        "Luggage does not fit on trip " + tripId + ", segment " + segmentIndex + ": requested "
            + requestedKg + " kg, available " + availableKg + " kg");
    this.segmentIndex = segmentIndex;
    this.requestedKg = requestedKg;
    this.availableKg = availableKg;
  }

  public int getSegmentIndex() {
    return segmentIndex;
  }

  public double getRequestedKg() {
    return requestedKg;
  }

  public double getAvailableKg() {
    return availableKg;
  }
}
