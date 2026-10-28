package transport.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Ділянка маршруту між двома сусідніми зупинками.
 *
 * @param from місто відправлення
 * @param to місто прибуття
 * @param distanceKm довжина ділянки, км
 * @param travelTime час у дорозі (без стоянок)
 */
public record RouteSegment(City from, City to, double distanceKm, Duration travelTime) {

  public RouteSegment {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Checks.requireDifferent(from.id(), to.id());
    Checks.requirePositive(distanceKm, "distanceKm");
    Checks.requirePositive(travelTime, "travelTime");
  }

  /**
   * Ділянка з відстанню по великому колу та часом за середньою швидкістю. Зручно для генерації
   * тестових даних.
   *
   * @param averageSpeedKmh середня швидкість, км/год
   */
  public static RouteSegment between(City from, City to, double averageSpeedKmh) {
    Checks.requirePositive(averageSpeedKmh, "averageSpeedKmh");
    double km = from.distanceKmTo(to);
    long minutes = Math.max(1, Math.round(km / averageSpeedKmh * 60));
    return new RouteSegment(from, to, km, Duration.ofMinutes(minutes));
  }
}
