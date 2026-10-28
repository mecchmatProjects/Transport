package transport.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Зупинка конкретного рейсу з обчисленими часами. Для першої зупинки {@code arrival == departure},
 * для останньої теж.
 *
 * @param stopIndex номер зупинки в маршруті, з 0
 * @param city місто
 * @param arrival прибуття
 * @param departure відправлення
 */
public record ScheduledStop(int stopIndex, City city, LocalDateTime arrival, LocalDateTime departure) {

  public ScheduledStop {
    Objects.requireNonNull(city, "city");
    Objects.requireNonNull(arrival, "arrival");
    Objects.requireNonNull(departure, "departure");
    if (departure.isBefore(arrival)) {
      throw new IllegalArgumentException("departure before arrival at " + city.id());
    }
  }
}
