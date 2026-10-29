package transport.optimization;

import java.time.Duration;
import java.util.Objects;

public record OptimizationResult(
    Schedule schedule, double cost, Duration runtime, ScheduleMetrics metrics) {

  public OptimizationResult {
    Objects.requireNonNull(schedule, "schedule");
    Objects.requireNonNull(runtime, "runtime");
    Objects.requireNonNull(metrics, "metrics");
  }
}
