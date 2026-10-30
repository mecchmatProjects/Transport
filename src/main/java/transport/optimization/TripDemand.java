package transport.optimization;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import transport.model.Route;

public record TripDemand(
    String id,
    Route route,
    LocalDateTime preferredDeparture,
    Duration flex,
    int priority,
    int expectedPassengers,
    double expectedLuggageKg) {

  public TripDemand {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(route, "route");
    Objects.requireNonNull(preferredDeparture, "preferredDeparture");
    Objects.requireNonNull(flex, "flex");
    if (flex.isNegative()) {
      throw new IllegalArgumentException("flex must be >= 0, got " + flex);
    }
    if (priority < 0 || expectedPassengers < 0 || expectedLuggageKg < 0) {
      throw new IllegalArgumentException("priority and expected load must be >= 0");
    }
  }

  public String originCityId() {
    return route.origin().id();
  }

  public String destinationCityId() {
    return route.destination().id();
  }

  public Duration duration() {
    return route.totalDuration();
  }

  public LocalDateTime earliestDeparture() {
    return preferredDeparture.minus(flex);
  }

  public LocalDateTime latestDeparture() {
    return preferredDeparture.plus(flex);
  }
}
