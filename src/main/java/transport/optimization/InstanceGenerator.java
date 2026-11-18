package transport.optimization;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import transport.model.Bus;
import transport.model.City;
import transport.model.Route;
import transport.model.RouteSegment;

/**
 * Випадкова задача для експериментів. Один seed дає ту саму задачу. Маршрути зв'язують усі міста,
 * тож порожній підгін між будь-якими містами можливий.
 */
public final class InstanceGenerator {

  static final double SPEED_KMH = 70;
  static final ScheduleConstraints CONSTRAINTS =
      new ScheduleConstraints(Duration.ofMinutes(30), Duration.ofHours(10), SPEED_KMH);
  static final LocalDate DAY = LocalDate.of(2026, 11, 2);

  private static final Duration DWELL = Duration.ofMinutes(10);
  private static final double MIN_CITY_DISTANCE_KM = 20;
  private static final int[] SEATS = {30, 45, 50};
  private static final int[] FLEX_MINUTES = {0, 15, 30, 60};

  private final long seed;

  public InstanceGenerator(long seed) {
    this.seed = seed;
  }

  public ProblemInstance generate(ExperimentSize size) {
    Random random = new Random(seed);
    List<City> cities = cities(size.cities(), random);
    List<Route> routes = routes(cities, size.routes(), random);
    List<Bus> buses = buses(size.buses(), routes, random);
    int maxSeats = buses.stream().mapToInt(Bus::getSeats).max().orElseThrow();
    List<TripDemand> demands = demands(size.trips(), routes, maxSeats, random);
    return new ProblemInstance(CityGraph.fromRoutes(routes), buses, demands, CONSTRAINTS);
  }

  private static List<City> cities(int count, Random random) {
    List<City> cities = new ArrayList<>();
    while (cities.size() < count) {
      // область приблизно 170 x 350 км, міста не ближче 20 км одне до одного;
      // понад сотню міст сюди вже не влізе
      int i = cities.size() + 1;
      City city =
          new City(
              String.format("C%02d", i),
              "City " + i,
              49.5 + 1.5 * random.nextDouble(),
              27 + 5 * random.nextDouble());
      if (cities.stream().allMatch(c -> c.distanceKmTo(city) >= MIN_CITY_DISTANCE_KM)) {
        cities.add(city);
      }
    }
    return cities;
  }

  // Кожен маршрут стартує з уже охопленого міста і веде через найближчі неохоплені.
  // Коли охоплено всі, решта маршрутів додає обхідні шляхи між сусідніми містами.
  private static List<Route> routes(List<City> cities, int count, Random random) {
    List<City> uncovered = new ArrayList<>(cities);
    List<City> covered = new ArrayList<>();
    covered.add(uncovered.remove(random.nextInt(uncovered.size())));
    List<Route> routes = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      List<City> stops = new ArrayList<>();
      stops.add(covered.get(random.nextInt(covered.size())));
      int newCities = Math.ceilDiv(uncovered.size(), count - i);
      if (newCities > 0) {
        for (int k = 0; k < newCities; k++) {
          City next = byDistance(stops.get(stops.size() - 1), uncovered).get(0);
          uncovered.remove(next);
          covered.add(next);
          stops.add(next);
        }
      } else {
        int segments = 2 + random.nextInt(2);
        for (int k = 0; k < segments; k++) {
          List<City> candidates = new ArrayList<>(cities);
          candidates.removeAll(stops);
          List<City> near = byDistance(stops.get(stops.size() - 1), candidates);
          if (near.isEmpty()) {
            break;
          }
          stops.add(near.get(random.nextInt(Math.min(3, near.size()))));
        }
      }
      routes.add(route(i + 1, stops));
    }
    return routes;
  }

  private static List<City> byDistance(City from, List<City> candidates) {
    return candidates.stream()
        .sorted(Comparator.comparingDouble(from::distanceKmTo).thenComparing(City::id))
        .toList();
  }

  private static Route route(int number, List<City> stops) {
    List<RouteSegment> segments = new ArrayList<>();
    for (int i = 0; i + 1 < stops.size(); i++) {
      segments.add(RouteSegment.between(stops.get(i), stops.get(i + 1), SPEED_KMH));
    }
    String name = stops.get(0).name() + " - " + stops.get(stops.size() - 1).name();
    return Route.withUniformDwell(number, name, segments, DWELL);
  }

  private static List<Bus> buses(int count, List<Route> routes, Random random) {
    List<Bus> buses = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      int seats = SEATS[random.nextInt(SEATS.length)];
      double luggageKg = 1000 + 100 * random.nextInt(6);
      String depot = routes.get(random.nextInt(routes.size())).origin().id();
      buses.add(new Bus(String.format("B%02d", i), "Generic", seats, luggageKg, depot));
    }
    return buses;
  }

  // пасажирів не більше, ніж місць у найбільшому автобусі, багаж менший за будь-який багажник
  private static List<TripDemand> demands(
      int count, List<Route> routes, int maxSeats, Random random) {
    List<TripDemand> demands = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      Route route = routes.get(random.nextInt(routes.size()));
      LocalDateTime preferred = DAY.atTime(5, 0).plusMinutes(5L * random.nextInt(181));
      Duration flex = Duration.ofMinutes(FLEX_MINUTES[random.nextInt(FLEX_MINUTES.length)]);
      int priority = 1 + random.nextInt(3);
      int passengers = 10 + random.nextInt(maxSeats - 9);
      double luggageKg = 10 * random.nextInt(81);
      demands.add(
          new TripDemand(
              String.format("T%03d", i), route, preferred, flex, priority, passengers, luggageKg));
    }
    return demands;
  }
}
