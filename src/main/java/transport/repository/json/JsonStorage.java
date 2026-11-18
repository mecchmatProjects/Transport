package transport.repository.json;

import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import transport.exception.StorageException;
import transport.model.Bus;
import transport.model.City;
import transport.model.Luggage;
import transport.model.Route;
import transport.model.ScheduleTemplate;
import transport.model.Tariff;
import transport.model.Ticket;
import transport.model.Trip;
import transport.repository.LoadError;
import transport.repository.Repository;
import transport.repository.TariffKey;

/** Порядок завантаження: міста → маршрути → рейси (кожен рівень посилається на попередній). */
public final class JsonStorage {

  public static final String CITIES = "cities.json";
  public static final String BUSES = "buses.json";
  public static final String ROUTES = "routes.json";
  public static final String TARIFFS = "tariffs.json";
  public static final String SCHEDULE_TEMPLATES = "schedule-templates.json";
  public static final String TRIPS = "trips.json";
  public static final String TICKETS = "tickets.json";
  public static final String LUGGAGE = "luggage.json";

  private final Path dataDir;
  private final JsonRepository<City, String> cities;
  private final JsonRepository<Bus, String> buses;
  private final JsonRepository<Route, Integer> routes;
  private final JsonRepository<Tariff, TariffKey> tariffs;
  private final JsonRepository<ScheduleTemplate, String> scheduleTemplates;
  private final JsonRepository<Trip, String> trips;
  private final JsonRepository<Ticket, String> tickets;
  private final JsonRepository<Luggage, String> luggage;

  private JsonStorage(Path dataDir, boolean strict) {
    this.dataDir = Objects.requireNonNull(dataDir, "dataDir");
    JsonCodec codec = new JsonCodec();

    cities = repo(CITIES, codec, new TypeReference<List<City>>() {}, "City", City::id);
    codec = codec.withCities(cities.findAll());
    routes = repo(ROUTES, codec, new TypeReference<List<Route>>() {}, "Route", Route::number);
    codec = codec.withRoutes(routes.findAll());
    trips = repo(TRIPS, codec, new TypeReference<List<Trip>>() {}, "Trip", Trip::getId);

    buses = repo(BUSES, codec, new TypeReference<List<Bus>>() {}, "Bus", Bus::getId);
    tariffs = repo(TARIFFS, codec, new TypeReference<List<Tariff>>() {}, "Tariff", TariffKey::of);
    scheduleTemplates =
        repo(SCHEDULE_TEMPLATES, codec, new TypeReference<List<ScheduleTemplate>>() {},
            "ScheduleTemplate", ScheduleTemplate::id);
    tickets = repo(TICKETS, codec, new TypeReference<List<Ticket>>() {}, "Ticket",
        Ticket::getNumber);
    luggage = repo(LUGGAGE, codec, new TypeReference<List<Luggage>>() {}, "Luggage",
        Luggage::getId);

    List<LoadError> errors = loadErrors();
    if (strict && !errors.isEmpty()) {
      LoadError first = errors.get(0);
      String more = errors.size() > 1 ? " (+" + (errors.size() - 1) + " more)" : "";
      throw new StorageException(dataDir.resolve(first.file()) + ", record " + first.record()
          + ": " + first.message() + more, null);
    }
  }

  /** Відкриває сховище; будь-який некоректний запис кидає {@code StorageException}. */
  public static JsonStorage open(Path dataDir) {
    return new JsonStorage(dataDir, true);
  }

  /** Відкриває сховище без некоректних записів; їх список дає {@link #loadErrors()}. */
  public static JsonStorage openSkippingInvalid(Path dataDir) {
    return new JsonStorage(dataDir, false);
  }

  /** Відкинуті записи всіх файлів у порядку завантаження. */
  public List<LoadError> loadErrors() {
    List<LoadError> errors = new ArrayList<>();
    for (JsonRepository<?, ?> repo : all()) {
      errors.addAll(repo.loadResult().errors());
    }
    return List.copyOf(errors);
  }

  public Path dataDir() {
    return dataDir;
  }

  public Repository<City, String> cities() {
    return cities;
  }

  public Repository<Bus, String> buses() {
    return buses;
  }

  public Repository<Route, Integer> routes() {
    return routes;
  }

  public Repository<Tariff, TariffKey> tariffs() {
    return tariffs;
  }

  public Repository<ScheduleTemplate, String> scheduleTemplates() {
    return scheduleTemplates;
  }

  public Repository<Trip, String> trips() {
    return trips;
  }

  public Repository<Ticket, String> tickets() {
    return tickets;
  }

  public Repository<Luggage, String> luggage() {
    return luggage;
  }

  public void flushAll() {
    all().forEach(JsonRepository::flush);
  }

  // Порядок збігається з порядком завантаження в конструкторі
  private List<JsonRepository<?, ?>> all() {
    return List.of(cities, routes, trips, buses, tariffs, scheduleTemplates, tickets, luggage);
  }

  private <T, ID> JsonRepository<T, ID> repo(
      String fileName,
      JsonCodec codec,
      TypeReference<List<T>> type,
      String entityName,
      Function<? super T, ? extends ID> id) {
    return new JsonRepository<>(dataDir.resolve(fileName), codec, type, entityName, id);
  }
}
