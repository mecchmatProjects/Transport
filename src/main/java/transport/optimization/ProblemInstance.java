package transport.optimization;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import transport.model.Bus;

public record ProblemInstance(
    CityGraph graph, List<Bus> buses, List<TripDemand> demands, ScheduleConstraints constraints) {

  public ProblemInstance {
    Objects.requireNonNull(graph, "graph");
    Objects.requireNonNull(constraints, "constraints");
    buses = List.copyOf(buses);
    demands = List.copyOf(demands);
    requireUnique(buses.stream().map(Bus::getId).toList(), "bus");
    requireUnique(demands.stream().map(TripDemand::id).toList(), "demand");
  }

  private static void requireUnique(List<String> ids, String what) {
    Set<String> seen = new HashSet<>();
    for (String id : ids) {
      if (!seen.add(id)) {
        throw new IllegalArgumentException("duplicate " + what + " id " + id);
      }
    }
  }
}
