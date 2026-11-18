package transport.repository.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
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

/** Єдине місце налаштування Jackson; маршрути й рейси читаються лише з каталогом. */
public final class JsonCodec {

  static final String CITIES_ATTRIBUTE = "transport.cities";

  static final String ROUTES_ATTRIBUTE = "transport.routes";

  private final ObjectMapper mapper;
  private final Map<String, City> cities; // null — каталог не задано
  private final Map<Integer, Route> routes;

  public JsonCodec() {
    this(createMapper(), null, null);
  }

  private JsonCodec(ObjectMapper mapper, Map<String, City> cities, Map<Integer, Route> routes) {
    this.mapper = mapper;
    this.cities = cities;
    this.routes = routes;
  }

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

  public JsonCodec withCities(Collection<City> catalogue) {
    Map<String, City> byId = new LinkedHashMap<>();
    for (City city : catalogue) {
      if (byId.put(city.id(), city) != null) {
        throw new IllegalArgumentException("duplicate city id in catalogue: " + city.id());
      }
    }
    return new JsonCodec(mapper, Map.copyOf(byId), routes);
  }

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
      try {
        Files.writeString(tmp, json);
        move(tmp, file);
      } catch (IOException e) {
        deleteQuietly(tmp, e);
        throw e;
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

  /** Записи масиву з файлу; {@code null} у файлі дає порожній список, не масив — помилка. */
  public List<JsonNode> readArray(Path file) {
    String json;
    try {
      json = Files.readString(file);
    } catch (IOException e) {
      throw new StorageException("cannot read " + file + ": " + e.getMessage(), e);
    }
    JsonNode root;
    try {
      // дробові числа як BigDecimal, щоб ціна 420.00 не стала 420.0
      root = mapper.reader()
          .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .without(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
          .readTree(json);
    } catch (IOException e) {
      throw new StorageException(file + ": " + message(e), e);
    }
    if (root == null || root.isMissingNode()) {
      throw new StorageException(file + ": empty file", null);
    }
    if (root.isNull()) {
      return List.of();
    }
    if (!root.isArray()) {
      throw new StorageException(file + ": expected a JSON array", null);
    }
    List<JsonNode> records = new ArrayList<>(root.size());
    root.forEach(records::add);
    return records;
  }

  /** Розбирає один запис масиву {@code listType}; помилка — {@code StorageException}. */
  public <T> T readElement(JsonNode node, TypeReference<List<T>> listType) {
    JavaType elementType = mapper.getTypeFactory().constructType(listType).getContentType();
    try {
      return withCatalogue(mapper.readerFor(elementType)).readValue(node);
    } catch (IOException e) {
      // помилку конструктора моделі показуємо без обгортки Jackson
      Throwable cause = e.getCause();
      String reason = cause instanceof IllegalArgumentException ? cause.getMessage() : message(e);
      throw new StorageException(reason, e);
    }
  }

  private static void move(Path from, Path to) throws IOException {
    try {
      Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void deleteQuietly(Path tmp, IOException cause) {
    try {
      Files.deleteIfExists(tmp);
    } catch (IOException e) {
      cause.addSuppressed(e);
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
