package transport.optimization;

import java.time.Duration;
import java.util.Objects;

public record ScheduleConstraints(
    Duration minTurnaround, Duration maxDailyWork, double avgSpeedKmh) {

  public ScheduleConstraints {
    Objects.requireNonNull(minTurnaround, "minTurnaround");
    Objects.requireNonNull(maxDailyWork, "maxDailyWork");
    if (minTurnaround.isNegative() || !maxDailyWork.isPositive() || !(avgSpeedKmh > 0)) {
      throw new IllegalArgumentException("invalid schedule constraints");
    }
  }
}
