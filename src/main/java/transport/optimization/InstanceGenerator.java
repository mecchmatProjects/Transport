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
 * Задача для експериментів, однакова для однакового seed. Рейси беруться з еталонного розкладу без
 * конфліктів, тому всі рейси можна покрити.
 */
public final class InstanceGenerator {

  static final double SPEED_KMH = 70;
  static final ScheduleConstraints CONSTRAINTS =
      new ScheduleConstraints(Duration.ofMinutes(30), Duration.ofHours(10), SPEED_KMH);
  static final LocalDate DAY = LocalDate.of(2026, 11, 2);
  static final LocalDateTime FIRST_DEPARTURE = DAY.atTime(5, 0);
  static final LocalDateTime LAST_DEPARTURE = DAY.atTime(20, 0);

  private static final Duration DWELL = Duration.ofMinutes(10);
  private static final double MIN_CITY_DISTANCE_KM = 10;
  private static final int MAX_PLACEMENT_ATTEMPTS = 100_000;
  private static final int[] SEATS = {30, 45, 50};
  private static final int[] FLEX_MINUTES = {0, 15, 30, 60};
  private static final int PLAN_ATTEMPTS = 20;

  /** Задача і розклад, з якого взято рейси. */
  public record Generated(ProblemInstance problem, Schedule reference) {}

  private record Planned(Route route, Bus bus, LocalDateTime departure) {}

  private final long seed;

  public InstanceGenerator(long seed) {
    this.seed = seed;
  }

  public ProblemInstance generate(ExperimentSize size) {
    return generateWithReference(size).problem();
  }

  public Generated generateWithReference(ExperimentSize size) {
    Random random = new Random(seed);
    List<City> cities = cities(size.cities(), random);
    List<Route> routes = routes(cities, size.routes(), random);
    CityGraph graph = CityGraph.fromRoutes(routes);
    List<Bus> buses = buses(size.buses(), routes, random);
    List<Route> directed = withReverse(routes);
    // зрідка рейси не влазять, тоді пробуємо ще раз
    List<Planned> planned = null;
    for (int attempt = 0; attempt < PLAN_ATTEMPTS && planned == null; attempt++) {
      planned = plan(size.trips(), buses, directed, graph, random);
    }
    if (planned == null) {
      throw new IllegalArgumentException(
          "cannot fit " + size.trips() + " trips into " + size.buses() + " buses");
    }

    List<TripDemand> demands = new ArrayList<>();
    List<Assignment> assignments = new ArrayList<>();
    for (int i = 0; i < planned.size(); i++) {
      Planned trip = planned.get(i);
      String id = String.format("T%03d", i + 1);
      Duration flex = Duration.ofMinutes(FLEX_MINUTES[random.nextInt(FLEX_MINUTES.length)]);
      int priority = 1 + random.nextInt(3);
      int passengers = 10 + random.nextInt(trip.bus().getSeats() - 9);
      double luggageKg = 10 * random.nextInt(81);
      demands.add(
          new TripDemand(
              id, trip.route(), trip.departure(), flex, priority, passengers, luggageKg));
      assignments.add(new Assignment(id, trip.bus().getId(), trip.departure()));
    }
    Schedule reference = new Schedule(demands.stream().map(TripDemand::id).toList());
    assignments.forEach(reference::assign);
    return new Generated(new ProblemInstance(graph, buses, demands, CONSTRAINTS), reference);
  }

  private static List<City> cities(int count, Random random) {
    List<City> cities = new ArrayList<>();
    int attempts = 0;
    while (cities.size() < count) {
      // область приблизно 80 на 160 км
      if (++attempts > MAX_PLACEMENT_ATTEMPTS) {
        throw new IllegalArgumentException(
            "cannot place " + count + " cities at least " + MIN_CITY_DISTANCE_KM + " km apart");
      }
      int i = cities.size() + 1;
      City city =
          new City(
              String.format("C%02d", i),
              "City " + i,
              50 + 0.7 * random.nextDouble(),
              29 + 2.2 * random.nextDouble());
      if (cities.stream().allMatch(c -> c.distanceKmTo(city) >= MIN_CITY_DISTANCE_KM)) {
        cities.add(city);
      }
    }
    return cities;
  }

  // маршрути охоплюють усі міста, решта додає обхідні шляхи
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

  // зворотний напрям має той самий номер маршруту
  private static List<Route> withReverse(List<Route> routes) {
    List<Route> directed = new ArrayList<>(routes);
    for (Route route : routes) {
      directed.add(route(route.number(), route.stops().reversed()));
    }
    return directed;
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

  // автобуси по черзі дописують рейси за правилами ConflictChecker; null, якщо рейсів замало
  private static List<Planned> plan(
      int count, List<Bus> buses, List<Route> routes, CityGraph graph, Random random) {
    List<BusDay> days = buses.stream().map(BusDay::new).toList();
    List<Planned> planned = new ArrayList<>();
    for (int turn = 0; planned.size() < count; turn++) {
      if (days.stream().allMatch(d -> d.done)) {
        return null;
      }
      BusDay day = days.get(turn % days.size());
      if (day.done) {
        continue;
      }
      int pause = 5 * random.nextInt(4);
      Route route = pickRoute(day.city, routes, random);
      LocalDateTime departure = day.departureIfFits(route, pause, graph);
      if (departure == null) {
        // беремо той, що закінчиться найраніше
        route = null;
        for (Route candidate : routes) {
          LocalDateTime d = day.departureIfFits(candidate, 0, graph);
          if (d != null
              && (route == null
                  || d.plus(candidate.totalDuration())
                      .isBefore(departure.plus(route.totalDuration())))) {
            route = candidate;
            departure = d;
          }
        }
      }
      if (route == null) {
        day.done = true;
      } else {
        planned.add(new Planned(route, day.bus, departure));
        day.add(route, departure, graph);
      }
    }
    planned.sort(
        Comparator.comparing(Planned::departure).thenComparing(p -> p.bus().getId()));
    return planned;
  }

  private static final class BusDay {
    final Bus bus;
    String city;
    LocalDateTime free = FIRST_DEPARTURE;
    long workMinutes;
    boolean done;

    BusDay(Bus bus) {
      this.bus = bus;
      this.city = bus.getDepotCityId();
    }

    // null, якщо рейс не влазить у день
    LocalDateTime departureIfFits(Route route, int pause, CityGraph graph) {
      int deadhead = graph.deadheadMinutes(city, route.origin().id(), SPEED_KMH);
      LocalDateTime departure =
          roundUpToFiveMinutes(free.plusMinutes(deadhead)).plusMinutes(pause);
      long work = workMinutes + deadhead + route.totalDuration().toMinutes();
      boolean fits =
          !departure.isAfter(LAST_DEPARTURE)
              && work <= CONSTRAINTS.maxDailyWork().toMinutes();
      return fits ? departure : null;
    }

    void add(Route route, LocalDateTime departure, CityGraph graph) {
      workMinutes +=
          graph.deadheadMinutes(city, route.origin().id(), SPEED_KMH)
              + route.totalDuration().toMinutes();
      city = route.destination().id();
      free = departure.plus(route.totalDuration()).plus(CONSTRAINTS.minTurnaround());
    }
  }

  // рейс з міста, де стоїть автобус
  private static Route pickRoute(String cityId, List<Route> routes, Random random) {
    List<Route> fromHere = routes.stream().filter(r -> r.origin().id().equals(cityId)).toList();
    List<Route> choice = fromHere.isEmpty() ? routes : fromHere;
    return choice.get(random.nextInt(choice.size()));
  }

  private static LocalDateTime roundUpToFiveMinutes(LocalDateTime time) {
    return time.plusMinutes((5 - time.getMinute() % 5) % 5);
  }
}
