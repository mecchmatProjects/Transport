package transport.optimization;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import transport.model.Bus;
import transport.model.TimeWindow;

/**
 * Перший рейс автобуса починається з підгону з депо, кожен наступний з міста, де закінчився
 * попередній. Робочий час рахується за датою відправлення рейсу.
 */
public final class ConflictChecker {

  private static final Comparator<Assignment> BY_DEPARTURE =
      Comparator.comparing(Assignment::departure).thenComparing(Assignment::demandId);

  public List<Conflict> check(Schedule schedule, ProblemInstance problem) {
    Map<String, Bus> buses = busesById(problem);
    Map<String, TripDemand> demands = demandsById(problem);
    List<Conflict> conflicts = new ArrayList<>();
    for (String busId : schedule.usedBusIds()) {
      conflicts.addAll(
          checkBus(find(buses, busId, "bus"), schedule.assignmentsOf(busId), demands, problem));
    }
    return conflicts;
  }

  /** Розклад не змінюється. Час поза межами ±flex теж вважається недопустимим. */
  public boolean canAssign(Schedule schedule, Assignment candidate, ProblemInstance problem) {
    Map<String, TripDemand> demands = demandsById(problem);
    Bus bus = find(busesById(problem), candidate.busId(), "bus");
    TripDemand demand = find(demands, candidate.demandId(), "demand");
    if (candidate.departure().isBefore(demand.earliestDeparture())
        || candidate.departure().isAfter(demand.latestDeparture())) {
      return false;
    }
    List<Assignment> assignments = new ArrayList<>(schedule.assignmentsOf(candidate.busId()));
    assignments.removeIf(a -> a.demandId().equals(candidate.demandId()));
    assignments.add(candidate);
    assignments.sort(BY_DEPARTURE);
    return checkBus(bus, assignments, demands, problem).isEmpty();
  }

  private List<Conflict> checkBus(
      Bus bus,
      List<Assignment> assignments,
      Map<String, TripDemand> demands,
      ProblemInstance problem) {
    ScheduleConstraints constraints = problem.constraints();
    CityGraph graph = problem.graph();
    double speed = constraints.avgSpeedKmh();
    String busId = bus.getId();
    List<Conflict> conflicts = new ArrayList<>();
    Map<LocalDate, Duration> workPerDay = new TreeMap<>();

    Assignment prev = null;
    TripDemand prevDemand = null;
    for (Assignment current : assignments) {
      TripDemand demand = find(demands, current.demandId(), "demand");
      LocalDateTime start = current.departure();
      LocalDateTime end = start.plus(demand.duration());
      LocalDate day = start.toLocalDate();

      if (demand.expectedPassengers() > bus.getSeats()
          || demand.expectedLuggageKg() > bus.getLuggageCapacityKg()) {
        conflicts.add(new Conflict(ConflictType.CAPACITY, busId, current.demandId()));
      }

      // хвилини порожнього підгону перед рейсом; -1, якщо через конфлікт його не рахуємо
      int deadhead;
      if (prev == null) {
        deadhead = graph.deadheadMinutes(bus.getDepotCityId(), demand.originCityId(), speed);
        if (deadhead == Integer.MAX_VALUE
            || start.minusMinutes(deadhead).isBefore(day.atStartOfDay())) {
          conflicts.add(
              new Conflict(ConflictType.WRONG_LOCATION, busId, "depot -> " + current.demandId()));
          deadhead = -1;
        }
      } else {
        String pair = prev.demandId() + " -> " + current.demandId();
        LocalDateTime prevEnd = prev.departure().plus(prevDemand.duration());
        String from = prevDemand.destinationCityId();
        String to = demand.originCityId();
        deadhead = graph.deadheadMinutes(from, to, speed);
        if (start.isBefore(prevEnd)) {
          conflicts.add(new Conflict(ConflictType.OVERLAP, busId, pair));
          deadhead = -1;
        } else if (deadhead == Integer.MAX_VALUE) {
          conflicts.add(new Conflict(ConflictType.WRONG_LOCATION, busId, pair));
          deadhead = -1;
        } else {
          LocalDateTime ready = prevEnd.plusMinutes(deadhead).plus(constraints.minTurnaround());
          if (start.isBefore(ready)) {
            ConflictType type =
                from.equals(to) ? ConflictType.TURNAROUND : ConflictType.WRONG_LOCATION;
            conflicts.add(new Conflict(type, busId, pair));
          }
        }
      }

      // підгін вважається впритул перед рейсом, тож перевірка трохи суворіша за потрібну
      LocalDateTime busyFrom = deadhead > 0 ? start.minusMinutes(deadhead) : start;
      if (!bus.isAvailableFor(new TimeWindow(busyFrom, end))) {
        conflicts.add(new Conflict(ConflictType.BUS_UNAVAILABLE, busId, current.demandId()));
      }

      Duration work = demand.duration().plusMinutes(Math.max(deadhead, 0));
      workPerDay.merge(day, work, Duration::plus);
      prev = current;
      prevDemand = demand;
    }

    workPerDay.forEach(
        (day, work) -> {
          if (work.compareTo(constraints.maxDailyWork()) > 0) {
            conflicts.add(new Conflict(ConflictType.WORKING_HOURS, busId, day + ": " + work));
          }
        });
    return conflicts;
  }

  private static Map<String, Bus> busesById(ProblemInstance problem) {
    return problem.buses().stream().collect(Collectors.toMap(Bus::getId, Function.identity()));
  }

  private static Map<String, TripDemand> demandsById(ProblemInstance problem) {
    return problem.demands().stream()
        .collect(Collectors.toMap(TripDemand::id, Function.identity()));
  }

  private static <T> T find(Map<String, T> byId, String id, String what) {
    T value = byId.get(id);
    if (value == null) {
      throw new IllegalArgumentException("unknown " + what + " " + id);
    }
    return value;
  }
}
