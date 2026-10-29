package transport.optimization;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import transport.model.City;
import transport.model.Route;
import transport.model.RouteSegment;

/** Граф міст для порожніх підгонів. Ребра двосторонні, з дублікатів лишається коротше. */
public final class CityGraph {

  private final Map<String, Map<String, Double>> edges;
  private final Map<String, Map<String, Double>> distancesFrom = new HashMap<>();

  private CityGraph(Map<String, Map<String, Double>> edges) {
    this.edges = edges;
  }

  public static CityGraph fromRoutes(Collection<Route> routes) {
    Map<String, Map<String, Double>> edges = new HashMap<>();
    for (Route route : routes) {
      for (RouteSegment segment : route.segments()) {
        addEdge(edges, segment.from(), segment.to(), segment.distanceKm());
        addEdge(edges, segment.to(), segment.from(), segment.distanceKm());
      }
    }
    return new CityGraph(edges);
  }

  private static void addEdge(
      Map<String, Map<String, Double>> edges, City from, City to, double km) {
    edges.computeIfAbsent(from.id(), id -> new HashMap<>()).merge(to.id(), km, Math::min);
  }

  /** Найкоротша відстань, км; {@code POSITIVE_INFINITY}, якщо шляху немає. */
  public double shortestDistanceKm(String fromCityId, String toCityId) {
    Objects.requireNonNull(fromCityId, "fromCityId");
    Objects.requireNonNull(toCityId, "toCityId");
    if (fromCityId.equals(toCityId)) {
      return 0;
    }
    return distancesFrom
        .computeIfAbsent(fromCityId, this::dijkstra)
        .getOrDefault(toCityId, Double.POSITIVE_INFINITY);
  }

  /** Хвилини підгону, округлені вгору; {@code Integer.MAX_VALUE}, якщо шляху немає. */
  public int deadheadMinutes(String fromCityId, String toCityId, double avgSpeedKmh) {
    if (!(avgSpeedKmh > 0)) {
      throw new IllegalArgumentException("avgSpeedKmh must be > 0, got " + avgSpeedKmh);
    }
    double km = shortestDistanceKm(fromCityId, toCityId);
    if (Double.isInfinite(km)) {
      return Integer.MAX_VALUE;
    }
    // -1e-9: інакше похибка double (31.000000000000004) округлюється до 32
    return (int) Math.ceil(km * 60 / avgSpeedKmh - 1e-9);
  }

  public boolean contains(String cityId) {
    return edges.containsKey(cityId);
  }

  private Map<String, Double> dijkstra(String source) {
    Map<String, Double> dist = new HashMap<>();
    if (!edges.containsKey(source)) {
      return dist;
    }
    record Entry(String cityId, double km) {}
    PriorityQueue<Entry> queue = new PriorityQueue<>((a, b) -> Double.compare(a.km(), b.km()));
    dist.put(source, 0.0);
    queue.add(new Entry(source, 0));
    while (!queue.isEmpty()) {
      Entry current = queue.poll();
      if (current.km() > dist.get(current.cityId())) {
        continue; // застарілий запис
      }
      for (Map.Entry<String, Double> edge : edges.get(current.cityId()).entrySet()) {
        double km = current.km() + edge.getValue();
        if (km < dist.getOrDefault(edge.getKey(), Double.POSITIVE_INFINITY)) {
          dist.put(edge.getKey(), km);
          queue.add(new Entry(edge.getKey(), km));
        }
      }
    }
    return dist;
  }
}
