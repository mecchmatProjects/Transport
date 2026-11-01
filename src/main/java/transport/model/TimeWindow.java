package transport.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/** Напіввідкритий інтервал {@code [start; end)}. */
public record TimeWindow(LocalDateTime start, LocalDateTime end) {

  public TimeWindow {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (!end.isAfter(start)) {
      throw new IllegalArgumentException("end must be after start: " + start + " .. " + end);
    }
  }

  /** Інтервали, що лише торкаються ({@code a.end == b.start}), не перетинаються. */
  public boolean overlaps(TimeWindow other) {
    return start.isBefore(other.end) && other.start.isBefore(end);
  }

  public boolean contains(LocalDateTime moment) {
    return !moment.isBefore(start) && moment.isBefore(end);
  }

  public Duration duration() {
    return Duration.between(start, end);
  }

  public TimeWindow extendedBy(Duration extra) {
    Checks.requireNonNegative(extra, "extra");
    return new TimeWindow(start, end.plus(extra));
  }
}
