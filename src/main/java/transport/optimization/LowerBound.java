package transport.optimization;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Нижня оцінка кількості автобусів, docs/math-model.md §7. Рейс t' може йти після t, якщо це
 * можливо хоча б за найсприятливіших часів у межах вікон. Оцінка дорівнює кількості рейсів мінус
 * максимальне паросполучення в графі таких пар.
 */
public final class LowerBound {

  private LowerBound() {}

  public static int minBuses(ProblemInstance problem) {
    List<TripDemand> trips = problem.demands();
    if (trips.isEmpty()) {
      return 0;
    }
    List<List<Integer>> next = new ArrayList<>();
    for (TripDemand from : trips) {
      List<Integer> successors = new ArrayList<>();
      for (int j = 0; j < trips.size(); j++) {
        if (trips.get(j) != from && canFollow(from, trips.get(j), problem)) {
          successors.add(j);
        }
      }
      next.add(successors);
    }
    int[] matchedTo = new int[trips.size()];
    Arrays.fill(matchedTo, -1);
    int matching = 0;
    for (int i = 0; i < trips.size(); i++) {
      if (augment(i, next, matchedTo, new boolean[trips.size()])) {
        matching++;
      }
    }
    // з вікнами пари можуть утворити цикл, тоді різниця буде 0, хоча автобус потрібен
    return Math.max(1, trips.size() - matching);
  }

  private static boolean canFollow(TripDemand from, TripDemand to, ProblemInstance problem) {
    int deadhead =
        problem
            .graph()
            .deadheadMinutes(
                from.destinationCityId(), to.originCityId(), problem.constraints().avgSpeedKmh());
    if (deadhead == Integer.MAX_VALUE) {
      return false;
    }
    LocalDateTime ready =
        from.earliestDeparture()
            .plus(from.duration())
            .plusMinutes(deadhead)
            .plus(problem.constraints().minTurnaround());
    return !ready.isAfter(to.latestDeparture());
  }

  // алгоритм Куна: шукаємо шлях, що збільшує паросполучення
  private static boolean augment(
      int from, List<List<Integer>> next, int[] matchedTo, boolean[] visited) {
    for (int to : next.get(from)) {
      if (!visited[to]) {
        visited[to] = true;
        if (matchedTo[to] == -1 || augment(matchedTo[to], next, matchedTo, visited)) {
          matchedTo[to] = from;
          return true;
        }
      }
    }
    return false;
  }
}
