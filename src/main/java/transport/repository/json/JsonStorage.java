package transport.repository.json;

import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import transport.model.Bus;
import transport.model.City;
import transport.model.Luggage;
import transport.model.Route;
import transport.model.ScheduleTemplate;
import transport.model.Tariff;
import transport.model.Ticket;
import transport.model.Trip;
import transport.repository.Repository;
import transport.repository.TariffKey;

/**
 * Усі репозиторії над однією текою даних, по файлу на тип: {@code cities.json}, {@code
 * routes.json}, … Відсутній файл означає порожнє сховище.
 *
 * <p>Порядок завантаження важливий: міста → маршрути (посилаються на міста) → рейси (посилаються
 * на маршрути). Каталоги для посилань фіксуються при відкритті, тому дані, змінені в іншому
 * процесі, побачить лише новий {@code open}.
 */
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

  private JsonStorage(Path dataDir) {
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
  }

  /**
   * Відкриває сховище в теці {@code dataDir} і завантажує всі наявні файли.
   *
   * @throws transport.exception.StorageException якщо файл битий або посилається на неіснуюче
   *     місто чи маршрут
   */
  public static JsonStorage open(Path dataDir) {
    return new JsonStorage(dataDir);
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

  /** Переписує всі файли — напр. після змін змінних сутностей без {@code save}. */
  public void flushAll() {
    List.of(cities, buses, routes, tariffs, scheduleTemplates, trips, tickets, luggage)
        .forEach(JsonRepository::flush);
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
