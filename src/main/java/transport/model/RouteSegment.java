package transport.model;

import java.time.Duration;
import java.util.Objects;

/** {@code dwellAtEnd} — стоянка в місті {@code to}; на останній ділянці маршруту нуль. */
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

  public RouteSegment(City from, City to, double distanceKm, Duration travelTime) {
    this(from, to, distanceKm, travelTime, Duration.ZERO);
  }

  public static RouteSegment between(City from, City to, double averageSpeedKmh) {
    Checks.requirePositive(averageSpeedKmh, "averageSpeedKmh");
    double km = from.distanceKmTo(to);
    long minutes = Math.max(1, Math.round(km / averageSpeedKmh * 60));
    return new RouteSegment(from, to, km, Duration.ofMinutes(minutes));
  }

  public RouteSegment withDwellAtEnd(Duration dwell) {
    return new RouteSegment(from, to, distanceKm, travelTime, dwell);
  }
}
