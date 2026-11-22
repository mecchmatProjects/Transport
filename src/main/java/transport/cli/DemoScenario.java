package transport.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import transport.repository.LoadError;
import transport.repository.json.JsonStorage;

/** Зріз 1: дані й квитки, граничні випадки 1–8 з підсумковою таблицею. */
final class DemoScenario {

  private final Path dataDir;
  private final PrintStream out;

  DemoScenario(Path dataDir, PrintStream out) {
    this.dataDir = dataDir;
    this.out = out;
  }

  /** Повертає {@code true}, якщо основні дані чисті й усі випадки пройшли. */
  boolean run() {
    out.println("== Transport demo: slice 1 (data + tickets) ==");

    section("Seed data " + dataDir);
    boolean seedClean = Main.check(dataDir, out).loadErrors().isEmpty();

    List<CaseResult> results = new ArrayList<>();
    Path edge = dataDir.resolve("edge");
    results.add(unknownCity(edge.resolve("unknown-city")));
    results.add(invalidValues(edge.resolve("invalid-values")));
    results.add(duplicates(edge.resolve("duplicates")));

    section("Tickets, cases 4-8");
    results.addAll(new TicketDemo(dataDir, out).run());
    results.sort((x, y) -> Integer.compare(x.number(), y.number()));

    printSummary(results, seedClean);
    return seedClean && results.stream().allMatch(CaseResult::ok);
  }

  private CaseResult unknownCity(Path dir) {
    JsonStorage storage = open(1, dir);
    List<LoadError> errors = storage.loadErrors();
    boolean ok = errors.size() == 1
        && errors.get(0).file().equals(JsonStorage.ROUTES)
        && errors.get(0).message().contains("unknown city")
        && storage.routes().count() > 0;
    return new CaseResult(1, "Unknown city in a route", "route skipped, rest loaded",
        errors.size() + " skipped, " + storage.routes().count() + " routes loaded", ok);
  }

  private CaseResult invalidValues(Path dir) {
    JsonStorage storage = open(2, dir);
    long loaded = storage.cities().count() + storage.buses().count() + storage.luggage().count();
    int skipped = storage.loadErrors().size();
    return new CaseResult(2, "Blank, zero, negative values", "4 skipped, valid loaded",
        skipped + " skipped, " + loaded + " loaded", skipped == 4 && loaded == 3);
  }

  private CaseResult duplicates(Path dir) {
    JsonStorage storage = open(3, dir);
    List<String> files = storage.loadErrors().stream().map(LoadError::file).toList();
    String skipped = String.join(", ", files).replace(".json", "");
    boolean ok = files.equals(List.of(JsonStorage.BUSES, JsonStorage.TICKETS))
        && storage.buses().count() > 0 && storage.tickets().count() > 0;
    return new CaseResult(3, "Duplicate bus id and ticket", "duplicates skipped",
        "skipped in " + skipped, ok);
  }

  private JsonStorage open(int number, Path dir) {
    section("Case " + number + ": " + dir);
    return Main.check(dir, out);
  }

  private void printSummary(List<CaseResult> results, boolean seedClean) {
    section("Summary");
    String row = "%-3s %-30s %-32s %-32s %s%n";
    out.printf(row, "#", "Case", "Expected", "Actual", "");
    out.printf(row, "-", "Seed data", "no invalid records",
        seedClean ? "no invalid records" : "has invalid records", mark(seedClean));
    for (CaseResult r : results) {
      out.printf(row, r.number(), r.title(), r.expected(), r.actual(), mark(r.ok()));
    }
    long passed = results.stream().filter(CaseResult::ok).count();
    out.println("Result: " + passed + "/" + results.size() + " cases OK"
        + (seedClean ? "" : ", seed data has errors"));
  }

  private void section(String title) {
    out.println();
    out.println("-- " + title);
  }

  private static String mark(boolean ok) {
    return ok ? "OK" : "FAIL";
  }
}
