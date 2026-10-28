package transport.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Ділянка маршруту між двома сусідніми зупинками.
 *
 * <p>Стоянка задається окремо для кожної зупинки: {@code dwellAtEnd} — скільки автобус стоїть у
 * місті {@code to} перед наступною ділянкою. Для останньої ділянки маршруту вона має бути нульовою
 * (кінцева зупинка, далі рейс закінчується), це перевіряє {@link Route}.
 *
 * @param from місто відправлення
 * @param to місто прибуття
 * @param distanceKm довжина ділянки, км
 * @param travelTime час у дорозі (без стоянок)
 * @param dwellAtEnd стоянка в місті {@code to}
 */
public record RouteSegment(
    City from, City to, double distanceKm, Duration travelTime, Duration dwellAtEnd) {

  public RouteSegment {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Checks.requireDifferent(from.id(), to.id());
    Checks.requirePositive(distanceKm, "distanceKm");
    Checks.requirePositive(travelTime, "travelTime");
    Checks.requireNonNegative(dwellAtEnd, "dwellAtEnd");
  }

  /** Ділянка без стоянки в кінці (напр. остання ділянка маршруту). */
  public RouteSegment(City from, City to, double distanceKm, Duration travelTime) {
    this(from, to, distanceKm, travelTime, Duration.ZERO);
  }

  /**
   * Ділянка, де відстань рахується за Haversine, а час — за середньою швидкістю.
   *
   * @param averageSpeedKmh середня швидкість, км/год (> 0)
   */
  public static RouteSegment between(City from, City to, double averageSpeedKmh) {
    Checks.requirePositive(averageSpeedKmh, "averageSpeedKmh");
    double km = from.distanceKmTo(to);
    long minutes = Math.max(1, Math.round(km / averageSpeedKmh * 60));
    return new RouteSegment(from, to, km, Duration.ofMinutes(minutes));
  }

  /** Копія ділянки з іншою стоянкою в кінці. */
  public RouteSegment withDwellAtEnd(Duration dwell) {
    return new RouteSegment(from, to, distanceKm, travelTime, dwell);
  }
}
