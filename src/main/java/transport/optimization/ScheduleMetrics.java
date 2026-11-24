package transport.optimization;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import transport.model.Bus;

public record ScheduleMetrics(
    int busesUsed,
    double totalDistanceKm,
    double deadheadKm,
    long idleMinutes,
    double avgLoadFactor,
    int conflicts,
    int unassigned) {

  /**
   * Пробіг разом з підгонами, підгін з депо лише перед першим рейсом. Простій рахується між рейсами
   * одного дня без часу на підгін і розворот, як у docs/math-model.md §6.
   */
  public static ScheduleMetrics of(Schedule schedule, ProblemInstance problem) {
    Map<String, TripDemand> demands =
        problem.demands().stream().collect(Collectors.toMap(TripDemand::id, Function.identity()));
    double speed = problem.constraints().avgSpeedKmh();
    double tripKm = 0;
    double deadheadKm = 0;
    long idle = 0;
    double load = 0;
    int trips = 0;
    for (Bus bus : problem.buses()) {
      String city = bus.getDepotCityId();
      LocalDateTime free = null;
      for (Assignment a : schedule.assignmentsOf(bus.getId())) {
        TripDemand demand = demands.get(a.demandId());
        double km = problem.graph().shortestDistanceKm(city, demand.originCityId());
        if (Double.isFinite(km)) {
          deadheadKm += km;
        }
        if (free != null && free.toLocalDate().equals(a.departure().toLocalDate())) {
          long gap = Duration.between(free, a.departure()).toMinutes();
          long needed =
              problem.graph().deadheadMinutes(city, demand.originCityId(), speed)
                  + problem.constraints().minTurnaround().toMinutes();
          idle += Math.max(0, gap - needed);
        }
        tripKm += demand.route().totalDistanceKm();
        load += (double) demand.expectedPassengers() / bus.getSeats();
        trips++;
        city = demand.destinationCityId();
        free = a.departure().plus(demand.duration());
      }
    }
    return new ScheduleMetrics(
        schedule.usedBusIds().size(),
        tripKm + deadheadKm,
        deadheadKm,
        idle,
        trips == 0 ? 0 : load / trips,
        new ConflictChecker().check(schedule, problem).size(),
        schedule.unassignedDemandIds().size());
  }
}
