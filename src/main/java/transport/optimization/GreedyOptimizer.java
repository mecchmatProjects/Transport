package transport.optimization;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import transport.model.Bus;

/**
 * Спершу важливіші рейси, далі за бажаним часом відправлення. Для кожного автобуса береться
 * найближчий до бажаного час без конфліктів з кроком 5 хв у межах вікна, а з автобусів той, що
 * найменше чекає перед рейсом. Новий автобус береться, лише якщо задіяні не підходять, і з них той,
 * чиє депо найближче. Якщо не підходить жоден, рейс лишається непокритим.
 */
public final class GreedyOptimizer implements ScheduleOptimizer {

  private static final int STEP_MINUTES = 5;
  private static final long FROM_DEPOT = Long.MAX_VALUE / 2;

  private final CostFunction cost;
  private final ConflictChecker checker = new ConflictChecker();

  public GreedyOptimizer(CostFunction cost) {
    this.cost = cost;
  }

  @Override
  public String name() {
    return "Greedy";
  }

  @Override
  public OptimizationResult optimize(ProblemInstance problem) {
    long started = System.nanoTime();
    List<TripDemand> order = new ArrayList<>(problem.demands());
    order.sort(
        Comparator.comparingInt(TripDemand::priority)
            .reversed()
            .thenComparing(TripDemand::preferredDeparture)
            .thenComparing(TripDemand::id));
    Map<String, TripDemand> demands =
        order.stream().collect(Collectors.toMap(TripDemand::id, Function.identity()));
    Schedule schedule = new Schedule(order.stream().map(TripDemand::id).toList());
    for (TripDemand demand : order) {
      Assignment best = null;
      long bestScore = Long.MAX_VALUE;
      for (Bus bus : problem.buses()) {
        Assignment candidate = firstFit(schedule, demand, bus, problem);
        if (candidate != null) {
          long score = score(schedule, candidate, bus, demands, problem);
          if (score < bestScore) {
            best = candidate;
            bestScore = score;
          }
        }
      }
      if (best != null) {
        schedule.assign(best);
      }
    }
    Duration runtime = Duration.ofNanos(System.nanoTime() - started);
    return new OptimizationResult(
        schedule,
        cost.evaluate(schedule, problem),
        runtime,
        ScheduleMetrics.of(schedule, problem));
  }

  private Assignment firstFit(
      Schedule schedule, TripDemand demand, Bus bus, ProblemInstance problem) {
    for (LocalDateTime departure : departures(demand)) {
      Assignment candidate = new Assignment(demand.id(), bus.getId(), departure);
      if (checker.canAssign(schedule, candidate, problem)) {
        return candidate;
      }
    }
    return null;
  }

  // хвилини від кінця попереднього рейсу разом з підгоном і розворотом, бо підгін теж втрата;
  // без попереднього рейсу автобус їде з депо, такий варіант завжди гірший за задіяний автобус
  private static long score(
      Schedule schedule,
      Assignment candidate,
      Bus bus,
      Map<String, TripDemand> demands,
      ProblemInstance problem) {
    Assignment previous = null;
    for (Assignment a : schedule.assignmentsOf(candidate.busId())) {
      if (a.departure().isBefore(candidate.departure())) {
        previous = a;
      }
    }
    if (previous == null) {
      TripDemand demand = demands.get(candidate.demandId());
      return FROM_DEPOT
          + problem
              .graph()
              .deadheadMinutes(
                  bus.getDepotCityId(),
                  demand.originCityId(),
                  problem.constraints().avgSpeedKmh());
    }
    LocalDateTime end = previous.departure().plus(demands.get(previous.demandId()).duration());
    return Duration.between(end, candidate.departure()).toMinutes();
  }

  // бажаний час, потім по черзі раніше і пізніше
  private static List<LocalDateTime> departures(TripDemand demand) {
    List<LocalDateTime> result = new ArrayList<>();
    result.add(demand.preferredDeparture());
    for (long shift = STEP_MINUTES; shift <= demand.flex().toMinutes(); shift += STEP_MINUTES) {
      result.add(demand.preferredDeparture().minusMinutes(shift));
      result.add(demand.preferredDeparture().plusMinutes(shift));
    }
    return result;
  }
}
