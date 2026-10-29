package transport.optimization;

public record ScheduleMetrics(
    int busesUsed,
    double totalDistanceKm,
    double deadheadKm,
    long idleMinutes,
    double avgLoadFactor,
    int conflicts,
    int unassigned) {}
