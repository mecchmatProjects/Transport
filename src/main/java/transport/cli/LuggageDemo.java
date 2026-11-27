package transport.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import transport.config.AppConfig;
import transport.exception.InvalidStatusTransitionException;
import transport.exception.LuggageCapacityExceededException;
import transport.exception.TransportException;
import transport.exception.ValidationException;
import transport.model.Bus;
import transport.model.City;
import transport.model.Contact;
import transport.model.Luggage;
import transport.model.Passenger;
import transport.model.Route;
import transport.model.Tariff;
import transport.model.Ticket;
import transport.model.Trip;
import transport.repository.InMemoryRepository;
import transport.repository.Repository;
import transport.repository.TariffKey;
import transport.repository.json.JsonStorage;
import transport.service.LuggageService;
import transport.service.TariffService;
import transport.service.TicketService;
import transport.service.TripService;
import transport.service.tariff.FallbackTariffPolicy;

/** Облік багажу на рейсі маршруту 1: випадки 14-18; каталог з даних, зміни лише в пам'яті. */
final class LuggageDemo {

  private static final String TRIP_ID = "DEMO-1";
  private static final String BUS_ID = "DEMO-MINI";
  private static final Contact SENDER = new Contact("Olena Shevchuk", null);
  private static final Contact RECIPIENT = new Contact("Taras Bondar", "+380671234567");

  private final PrintStream out;
  private final LuggageService luggage;
  private final TicketService tickets;
  private final Route route;

  LuggageDemo(Path dataDir, PrintStream out) {
    this.out = out;
    JsonStorage storage = JsonStorage.openSkippingInvalid(dataDir);
    Repository<City, String> cities = copy("City", City::id, storage.cities());
    Repository<Route, Integer> routes = copy("Route", Route::number, storage.routes());
    Repository<Tariff, TariffKey> tariffs = copy("Tariff", TariffKey::of, storage.tariffs());
    Repository<Bus, String> buses = new InMemoryRepository<>("Bus", Bus::getId);
    Repository<Trip, String> trips = new InMemoryRepository<>("Trip", Trip::getId);
    Repository<Ticket, String> ticketRepo = new InMemoryRepository<>("Ticket", Ticket::getNumber);
    Repository<Luggage, String> luggageRepo = new InMemoryRepository<>("Luggage", Luggage::getId);

    route = routes.getById(1);
    // 100 кг багажної місткості, щоб вона закінчилась за кілька реєстрацій
    buses.add(new Bus(BUS_ID, "Demo minibus", 2, 100, route.origin().id()));
    TripService tripService = new TripService(trips, routes, buses, ticketRepo, luggageRepo);
    tripService.create(new Trip(TRIP_ID, route, LocalDate.of(2026, 12, 1), LocalTime.of(8, 0)));
    tripService.assignBus(TRIP_ID, BUS_ID);

    TariffService tariffService = new TariffService(tariffs, cities, routes);
    var pricing = FallbackTariffPolicy.tableThenDistance(
        tariffService, AppConfig.load(dataDir).tariff());
    tickets = new TicketService(ticketRepo, trips, buses, pricing);
    luggage = new LuggageService(luggageRepo, ticketRepo, trips, buses, pricing);
  }

  /** Повертає {@code true}, якщо всі випадки пройшли. */
  boolean run() {
    List<City> stops = route.stops();
    String a = stops.get(0).id();
    String b = stops.get(1).id();
    String c = stops.get(stops.size() - 2).id();
    String d = stops.get(stops.size() - 1).id();
    List<CaseResult> results = new ArrayList<>();
    out.println("== Transport demo: luggage ==");
    out.println("Trip " + TRIP_ID + ", route " + route.number() + " " + route.name()
        + ", bus " + BUS_ID + " with 100 kg of luggage");

    step("Fill the last segment " + c + " -> " + d + " up to 99 kg");
    Ticket ticket = tickets.sell(TRIP_ID, new Passenger("P1", "Ivan Petrenko", null), c, d);
    out.println("  SOLD " + ticket.getNumber() + ": " + c + " -> " + d);
    printRegistered(luggage.registerPassengerLuggage(ticket.getNumber(), 39, 0.2));
    Luggage big = shipment(c, d, 60);
    showLoad();

    step("Case 14: the last kilogram on " + c + " -> " + d);
    Optional<TransportException> last = attempt(() -> shipment(c, d, 1));
    double left = luggage.availableKg(TRIP_ID, c, d);
    results.add(new CaseResult(14, "Last kilogram on a segment", "registered, 0 kg left",
        last.map(LuggageDemo::describe).orElse("registered, " + Math.round(left) + " kg left"),
        last.isEmpty() && left == 0));
    showLoad();

    step("Case 15: " + a + " -> " + d + " needs the full last segment");
    results.add(refusal(15, "Full segment inside the leg", LuggageCapacityExceededException.class,
        attempt(() -> shipment(a, d, 1))));
    step("The free segments still accept " + a + " -> " + c);
    shipment(a, c, 10);
    showLoad();

    step("Case 16: negative weight");
    results.add(refusal(16, "Negative weight", ValidationException.class,
        attempt(() -> shipment(a, b, -5))));

    step("Case 17: wrong direction " + d + " -> " + a);
    results.add(refusal(17, "from after to", ValidationException.class,
        attempt(() -> shipment(d, a, 5))));

    step("Case 18: cancel twice");
    String id = big.getId();
    cancel(id);
    results.add(refusal(18, "Cancel a cancelled shipment",
        InvalidStatusTransitionException.class, attempt(() -> cancel(id))));
    showLoad();

    return printSummary(results);
  }

  private static CaseResult refusal(int number, String title,
      Class<? extends TransportException> expected, Optional<TransportException> error) {
    return new CaseResult(number, title, describe(expected),
        error.map(LuggageDemo::describe).orElse("accepted"),
        error.filter(expected::isInstance).isPresent());
  }

  private static String describe(TransportException e) {
    return describe(e.getClass());
  }

  private static String describe(Class<? extends TransportException> type) {
    return "refused: " + type.getSimpleName().replace("Exception", "");
  }

  private Luggage shipment(String from, String to, double kg) {
    Luggage item = luggage.registerShipment(TRIP_ID, from, to, kg, 0.1, SENDER, RECIPIENT);
    printRegistered(item);
    return item;
  }

  private void printRegistered(Luggage item) {
    out.printf("  REGISTERED %s: %s -> %s, %s kg, %s UAH%n", item.getId(), item.getFromCityId(),
        item.getToCityId(), item.getWeightKg(), item.getPrice());
  }

  private Luggage cancel(String id) {
    Luggage item = luggage.cancel(id);
    out.println("  CANCELLED " + id);
    return item;
  }

  // Відмову повертає як значення, щоб порівняти її з очікуваною
  private Optional<TransportException> attempt(Supplier<Luggage> action) {
    try {
      action.get();
      return Optional.empty();
    } catch (TransportException e) {
      out.println("  REJECTED: " + e.getMessage());
      return Optional.of(e);
    }
  }

  private void showLoad() {
    List<City> stops = route.stops();
    List<Double> load = luggage.loadBySegment(TRIP_ID);
    StringBuilder line = new StringBuilder("  load, kg:");
    for (int i = 0; i < load.size(); i++) {
      line.append(' ').append(stops.get(i).id()).append('-').append(stops.get(i + 1).id())
          .append('=').append(Math.round(load.get(i)));
    }
    out.println(line);
  }

  private boolean printSummary(List<CaseResult> results) {
    out.println();
    out.println("-- Summary");
    String row = "%-3s %-30s %-32s %-32s %s%n";
    out.printf(row, "#", "Case", "Expected", "Actual", "");
    for (CaseResult r : results) {
      out.printf(row, r.number(), r.title(), r.expected(), r.actual(), r.ok() ? "OK" : "FAIL");
    }
    long passed = results.stream().filter(CaseResult::ok).count();
    out.println("Result: " + passed + "/" + results.size() + " cases OK");
    return passed == results.size();
  }

  private void step(String title) {
    out.println();
    out.println(title);
  }

  private static <T, ID> Repository<T, ID> copy(
      String name, Function<T, ID> id, Repository<T, ID> source) {
    Repository<T, ID> target = new InMemoryRepository<>(name, id);
    source.findAll().forEach(target::add);
    return target;
  }
}
