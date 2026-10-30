package transport.optimization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/** Змінюваний, щоб ходи LS і SA працювали на місці й відкочувались без копіювання. */
public final class Schedule {

  private static final Comparator<Assignment> BY_DEPARTURE =
      Comparator.comparing(Assignment::departure).thenComparing(Assignment::demandId);

  private final Set<String> demandIds;
  private final Map<String, List<Assignment>> byBus = new TreeMap<>();
  private final Map<String, Assignment> byDemand = new HashMap<>();

  public Schedule(Collection<String> demandIds) {
    this.demandIds = new LinkedHashSet<>(demandIds);
  }

  public void assign(Assignment assignment) {
    String demandId = assignment.demandId();
    if (!demandIds.contains(demandId)) {
      throw new IllegalArgumentException("unknown demand " + demandId);
    }
    if (byDemand.containsKey(demandId)) {
      throw new IllegalStateException("demand " + demandId + " is already assigned");
    }
    List<Assignment> list = byBus.computeIfAbsent(assignment.busId(), id -> new ArrayList<>());
    int pos = 0;
    while (pos < list.size() && BY_DEPARTURE.compare(list.get(pos), assignment) < 0) {
      pos++;
    }
    list.add(pos, assignment);
    byDemand.put(demandId, assignment);
  }

  /** Повертає зняте призначення для undo. */
  public Optional<Assignment> unassign(String demandId) {
    Assignment removed = byDemand.remove(demandId);
    if (removed == null) {
      return Optional.empty();
    }
    List<Assignment> list = byBus.get(removed.busId());
    list.remove(removed);
    if (list.isEmpty()) {
      byBus.remove(removed.busId());
    }
    return Optional.of(removed);
  }

  public List<Assignment> assignmentsOf(String busId) {
    return List.copyOf(byBus.getOrDefault(busId, List.of()));
  }

  public Optional<Assignment> assignmentOf(String demandId) {
    return Optional.ofNullable(byDemand.get(demandId));
  }

  /** Відсортовано, щоб той самий seed давав той самий результат. */
  public SortedSet<String> usedBusIds() {
    return Collections.unmodifiableSortedSet(new TreeSet<>(byBus.keySet()));
  }

  public Set<String> unassignedDemandIds() {
    Set<String> result = new LinkedHashSet<>(demandIds);
    result.removeAll(byDemand.keySet());
    return result;
  }

  public Schedule copy() {
    Schedule copy = new Schedule(demandIds);
    byDemand.values().forEach(copy::assign);
    return copy;
  }
}
