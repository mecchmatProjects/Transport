package transport.repository.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import transport.exception.StorageException;
import transport.model.Bus;
import transport.model.City;
import transport.model.Luggage;
import transport.model.PassengerLuggage;
import transport.model.Route;
import transport.model.RouteSegment;
import transport.model.Shipment;
import transport.model.Tariff;
import transport.model.Ticket;
import transport.model.Trip;

/**
 * Читання і запис моделі в JSON. Єдине місце, де налаштовано Jackson; {@code JsonRepository}
 * (K1) працює через нього.
 *
 * <ul>
 *   <li>дати й тривалості — ISO-рядки ({@code "2026-09-23T08:00:00"}, {@code "PT2H30M"});
 *   <li>маршрути посилаються на міста за id, тому для читання маршрутів потрібен каталог міст:
 *       {@link #withCities(Collection)}; рейси так само посилаються на маршрути за номером:
 *       {@link #withRoutes(Collection)};
 *   <li>багаж поліморфний, підклас — у полі {@code "kind"} ({@code PASSENGER} / {@code SHIPMENT}).
 *       Колекції пишіть через {@link #write(Object, TypeReference)}: через стирання типів Jackson
 *       інакше не знає, що елементи {@code List<Luggage>} — поліморфні, і не запише {@code kind};
 *   <li>невідомі поля — помилка: це ловить одруківки в JSON, який редагують руками.
 * </ul>
 *
 * Будь-яка помилка вводу-виводу чи формату загортається в {@link StorageException}.
 */
public final class JsonCodec {

  /** Атрибут десеріалізації з каталогом міст {@code Map<String, City>}. */
  static final String CITIES_ATTRIBUTE = "transport.cities";

  /** Атрибут десеріалізації з каталогом маршрутів {@code Map<Integer, Route>}. */
  static final String ROUTES_ATTRIBUTE = "transport.routes";

  private final ObjectMapper mapper;
  private final Map<String, City> cities; // null — каталог не задано
  private final Map<Integer, Route> routes; // null — каталог не задано

  public JsonCodec() {
    this(createMapper(), null, null);
  }

  private JsonCodec(ObjectMapper mapper, Map<String, City> cities, Map<Integer, Route> routes) {
    this.mapper = mapper;
    this.cities = cities;
    this.routes = routes;
  }

  /** Налаштований {@link ObjectMapper} з усіма модулями й mix-in моделі. */
  public static ObjectMapper createMapper() {
    SimpleModule model = new SimpleModule("transport-model");
    model.setMixInAnnotation(Luggage.class, ModelMixins.LuggageMixin.class);
    model.setMixInAnnotation(PassengerLuggage.class, ModelMixins.PassengerLuggageMixin.class);
    model.setMixInAnnotation(Shipment.class, ModelMixins.ShipmentMixin.class);
    model.setMixInAnnotation(Bus.class, ModelMixins.BusMixin.class);
    model.setMixInAnnotation(Ticket.class, ModelMixins.TicketMixin.class);
    model.setMixInAnnotation(Tariff.class, ModelMixins.TariffMixin.class);
    model.addSerializer(RouteSegment.class, new RouteSegmentJson.Serializer());
    model.addDeserializer(RouteSegment.class, new RouteSegmentJson.Deserializer());
    model.addSerializer(Trip.class, new TripJson.Serializer());
    model.addDeserializer(Trip.class, new TripJson.Deserializer());

    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(model)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
  }

  /** Копія кодека, яка під час читання розв'язує id міст через цей каталог. */
  public JsonCodec withCities(Collection<City> catalogue) {
    Map<String, City> byId = new LinkedHashMap<>();
    for (City city : catalogue) {
      if (byId.put(city.id(), city) != null) {
        throw new IllegalArgumentException("duplicate city id in catalogue: " + city.id());
      }
    }
    return new JsonCodec(mapper, Map.copyOf(byId), routes);
  }

  /** Копія кодека, яка під час читання розв'язує номери маршрутів через цей каталог. */
  public JsonCodec withRoutes(Collection<Route> catalogue) {
    Map<Integer, Route> byNumber = new LinkedHashMap<>();
    for (Route route : catalogue) {
      if (byNumber.put(route.number(), route) != null) {
        throw new IllegalArgumentException("duplicate route number in catalogue: " + route.number());
      }
    }
    return new JsonCodec(mapper, cities, Map.copyOf(byNumber));
  }

  public String write(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (IOException e) {
      throw new StorageException("cannot write JSON: " + message(e), e);
    }
  }

  /** Запис з явним типом — обов'язково для колекцій поліморфних сутностей. */
  public <T> String write(T value, TypeReference<T> type) {
    try {
      return mapper.writerFor(type).writeValueAsString(value);
    } catch (IOException e) {
      throw new StorageException("cannot write JSON: " + message(e), e);
    }
  }

  public <T> T read(String json, Class<T> type) {
    try {
      return reader(type).readValue(json);
    } catch (IOException e) {
      throw new StorageException("cannot read " + type.getSimpleName() + ": " + message(e), e);
    }
  }

  public <T> T read(String json, TypeReference<T> type) {
    try {
      return reader(type).readValue(json);
    } catch (IOException e) {
      throw new StorageException("cannot read " + type.getType() + ": " + message(e), e);
    }
  }

  /** Записує файл атомарно: спершу в тимчасовий поруч, потім перейменовує. */
  public <T> void writeFile(Path file, T value, TypeReference<T> type) {
    Objects.requireNonNull(file, "file");
    String json = write(value, type);
    try {
      Path dir = file.toAbsolutePath().getParent();
      Files.createDirectories(dir);
      Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
      Files.writeString(tmp, json);
      try {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new StorageException("cannot write " + file + ": " + e.getMessage(), e);
    }
  }

  public <T> T readFile(Path file, TypeReference<T> type) {
    String json;
    try {
      json = Files.readString(file);
    } catch (IOException e) {
      throw new StorageException("cannot read " + file + ": " + e.getMessage(), e);
    }
    try {
      return read(json, type);
    } catch (StorageException e) {
      throw new StorageException(file + ": " + e.getMessage(), e.getCause());
    }
  }

  private ObjectReader reader(Class<?> type) {
    return withCatalogue(mapper.readerFor(type));
  }

  private ObjectReader reader(TypeReference<?> type) {
    return withCatalogue(mapper.readerFor(type));
  }

  private ObjectReader withCatalogue(ObjectReader reader) {
    if (cities != null) {
      reader = reader.withAttribute(CITIES_ATTRIBUTE, cities);
    }
    if (routes != null) {
      reader = reader.withAttribute(ROUTES_ATTRIBUTE, routes);
    }
    return reader;
  }

  private static String message(IOException e) {
    return e instanceof com.fasterxml.jackson.core.JsonProcessingException jpe
        ? jpe.getOriginalMessage()
        : e.getMessage();
  }
}
