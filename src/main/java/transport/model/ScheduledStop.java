package transport.model;

import java.time.LocalDateTime;
import java.util.Objects;

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
