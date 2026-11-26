package transport.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import transport.exception.TransportException;
import transport.repository.LoadError;
import transport.repository.json.JsonStorage;

/** Точка входу: перевірка даних і демо-сценарії зрізів. */
public final class Main {

  private static final Path DEFAULT_DATA = Path.of("data");

  private Main() {}

  public static void main(String[] args) {
    PrintStream out = System.out;
    String command = args.length > 0 ? args[0] : "help";
    Path dataDir = args.length > 1 ? Path.of(args[1]) : DEFAULT_DATA;
    try {
      switch (command) {
        case "check" -> check(dataDir, out);
        case "tickets-demo" -> new TicketDemo(dataDir, out).run();
        case "demo" -> {
          if (!new DemoScenario(dataDir, out).run()) {
            System.exit(1);
          }
        }
        case "luggage-demo" -> {
          if (!new LuggageDemo(dataDir, out).run()) {
            System.exit(1);
          }
        }
        default -> usage(out);
      }
    } catch (TransportException e) {
      out.println("Error: " + e.getMessage());
      System.exit(1);
    }
  }

  // Граничні випадки 1–3: некоректні записи відкидаються, решта даних завантажується
  static JsonStorage check(Path dataDir, PrintStream out) {
    JsonStorage storage = JsonStorage.openSkippingInvalid(dataDir);
    out.printf("Loaded from %s: %d cities, %d routes, %d buses, %d tariffs, %d trips,"
            + " %d tickets, %d luggage%n",
        dataDir, storage.cities().count(), storage.routes().count(), storage.buses().count(),
        storage.tariffs().count(), storage.trips().count(), storage.tickets().count(),
        storage.luggage().count());
    List<LoadError> errors = storage.loadErrors();
    if (errors.isEmpty()) {
      out.println("No invalid records.");
      return storage;
    }
    out.println("Skipped " + errors.size() + " invalid record(s):");
    errors.forEach(e -> out.println("  " + e));
    return storage;
  }

  private static void usage(PrintStream out) {
    out.println("Usage:");
    out.println("  check [dataDir]          load data and list invalid records");
    out.println("  tickets-demo [dataDir]   sell and cancel tickets on a demo trip");
    out.println("  demo [dataDir]           slice 1: edge cases 1-8 with a summary table");
    out.println("  luggage-demo [dataDir]   register and cancel luggage, cases 14-18 with a table");
  }
}
