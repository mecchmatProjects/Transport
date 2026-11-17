package transport.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Function;
import transport.config.AppConfig;
import transport.exception.TransportException;
import transport.model.Bus;
import transport.model.City;
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
import transport.service.TariffService;
import transport.service.TicketService;
import transport.service.TripService;
import transport.service.tariff.FallbackTariffPolicy;

/** Граничні випадки 4–8 на рейсі маршруту 1; каталог береться з даних, зміни лише в пам'яті. */
final class TicketDemo {

  private static final String TRIP_ID = "DEMO-1";
  private static final String BUS_ID = "DEMO-MINI";

  private final PrintStream out;
  private final TicketService tickets;
  private final Route route;

  TicketDemo(Path dataDir, PrintStream out) {
    this.out = out;
    JsonStorage storage = JsonStorage.openSkippingInvalid(dataDir);
    Repository<City, String> cities = copy("City", City::id, storage.cities());
    Repository<Route, Integer> routes = copy("Route", Route::number, storage.routes());
    Repository<Tariff, TariffKey> tariffs = copy("Tariff", TariffKey::of, storage.tariffs());
    Repository<Bus, String> buses = new InMemoryRepository<>("Bus", Bus::getId);
    Repository<Trip, String> trips = new InMemoryRepository<>("Trip", Trip::getId);
    Repository<Ticket, String> ticketRepo = new InMemoryRepository<>("Ticket", Ticket::getNumber);
    Repository<Luggage, String> luggage = new InMemoryRepository<>("Luggage", Luggage::getId);

    route = routes.getById(1);
    // Маленький автобус, щоб місця закінчились за кілька продажів
    buses.add(new Bus(BUS_ID, "Demo minibus", 2, 200, route.origin().id()));
    TripService tripService = new TripService(trips, routes, buses, ticketRepo, luggage);
    tripService.create(new Trip(TRIP_ID, route, LocalDate.of(2026, 12, 1), LocalTime.of(8, 0)));
    tripService.assignBus(TRIP_ID, BUS_ID);

    TariffService tariffService = new TariffService(tariffs, cities, routes);
    tickets = new TicketService(ticketRepo, trips, buses,
        FallbackTariffPolicy.tableThenDistance(tariffService, AppConfig.load(dataDir).tariff()));
  }

  void run() {
    List<City> stops = route.stops();
    String a = stops.get(0).id();
    String b = stops.get(1).id();
    String c = stops.get(stops.size() - 2).id();
    String d = stops.get(stops.size() - 1).id();
    out.println("Trip " + TRIP_ID + ", route " + route.number() + " " + route.name()
        + ", bus " + BUS_ID + " with 2 seats");

    step("Fill the last segment " + c + " -> " + d);
    sell("P1", "Ivan Petrenko", c, d);
    sell("P2", "Olha Koval", c, d);
    showLoad();

    step("Case 6: " + a + " -> " + d + " needs the full last segment");
    sell("P3", "Petro Bondar", a, d);

    step("Case 4: the last seat on " + a + " -> " + c);
    sell("P3", "Petro Bondar", a, c);
    sell("P4", "Mariia Shevchenko", a, c);
    showLoad();

    step("Case 5: one more ticket " + a + " -> " + b);
    sell("P5", "Andrii Melnyk", a, b);

    step("Case 7: wrong direction " + d + " -> " + a);
    sell("P5", "Andrii Melnyk", d, a);

    step("Case 8: cancel twice");
    String number = tickets.findByTrip(TRIP_ID).get(0).getNumber();
    cancel(number);
    cancel(number);
    showLoad();
  }

  private void sell(String passengerId, String name, String from, String to) {
    try {
      Ticket t = tickets.sell(TRIP_ID, new Passenger(passengerId, name, null), from, to);
      out.printf("  SOLD %s: %s, %s -> %s, seat %d, %s UAH%n",
          t.getNumber(), name, from, to, t.getSeatNumber(), t.getPrice());
    } catch (TransportException e) {
      out.println("  REJECTED " + from + " -> " + to + ": " + e.getMessage());
    }
  }

  private void cancel(String number) {
    try {
      tickets.cancel(number);
      out.println("  CANCELLED " + number);
    } catch (TransportException e) {
      out.println("  REJECTED cancel " + number + ": " + e.getMessage());
    }
  }

  private void showLoad() {
    List<City> stops = route.stops();
    List<Integer> load = tickets.passengersBySegment(TRIP_ID);
    StringBuilder line = new StringBuilder("  load:");
    for (int i = 0; i < load.size(); i++) {
      line.append(' ').append(stops.get(i).id()).append('-').append(stops.get(i + 1).id())
          .append('=').append(load.get(i));
    }
    out.println(line);
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
