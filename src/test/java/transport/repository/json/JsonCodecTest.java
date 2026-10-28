package transport.repository.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import transport.exception.StorageException;
import transport.model.Bus;
import transport.model.BusStatus;
import transport.model.City;
import transport.model.Contact;
import transport.model.Luggage;
import transport.model.LuggageStatus;
import transport.model.PassengerLuggage;
import transport.model.Route;
import transport.model.RouteSegment;
import transport.model.ScheduleTemplate;
import transport.model.Shipment;
import transport.model.TimeWindow;

class JsonCodecTest {

  private static final City KYIV = new City("KYIV", "Kyiv", 50.4501, 30.5234);
  private static final City ZHYTOMYR = new City("ZHYTOMYR", "Zhytomyr", 50.2547, 28.6587);
  private static final City RIVNE = new City("RIVNE", "Rivne", 50.6199, 26.2516);
  private static final List<City> CITIES = List.of(KYIV, ZHYTOMYR, RIVNE);

  private static final TypeReference<List<Luggage>> LUGGAGE_LIST = new TypeReference<>() {};
  private static final TypeReference<List<City>> CITY_LIST = new TypeReference<>() {};

  private final JsonCodec codec = new JsonCodec().withCities(CITIES);

  @TempDir Path dir;

  private static Route route() {
    return new Route(
        12,
        "Kyiv - Rivne",
        List.of(
            new RouteSegment(KYIV, ZHYTOMYR, 140, Duration.ofMinutes(120), Duration.ofMinutes(10)),
            new RouteSegment(ZHYTOMYR, RIVNE, 190, Duration.ofMinutes(150))));
  }

  @Test
  void cityRecordRoundTrip() {
    String json = new JsonCodec().write(KYIV);
    assertThat(new JsonCodec().read(json, City.class)).isEqualTo(KYIV);
  }

  @Test
  void routeStoresCityIdsAndResolvesThemFromCatalogue() {
    String json = codec.write(route());
    assertThat(json).contains("\"from\" : \"KYIV\"");
    assertThat(json).contains("\"travelTime\" : \"PT2H\"");
    assertThat(json).contains("\"dwellAtEnd\" : \"PT10M\"");
    assertThat(json).doesNotContain("latitude");

    Route back = codec.read(json, Route.class);
    assertThat(back).isEqualTo(route());
    assertThat(back.origin()).isSameAs(KYIV);
    assertThat(back.totalDuration()).isEqualTo(Duration.ofMinutes(280));
  }

  @Test
  void routeWithUnknownCityFails() {
    String json = codec.write(route());
    JsonCodec withoutRivne = new JsonCodec().withCities(List.of(KYIV, ZHYTOMYR));
    assertThatThrownBy(() -> withoutRivne.read(json, Route.class))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("unknown city 'RIVNE'");
  }

  @Test
  void routeWithoutCatalogueFails() {
    String json = codec.write(route());
    assertThatThrownBy(() -> new JsonCodec().read(json, Route.class))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("city catalogue");
  }

  @Test
  void invalidRouteIsReportedAsStorageError() {
    String json =
        "{\"number\":1,\"name\":\"x\",\"segments\":[{\"from\":\"KYIV\",\"to\":\"KYIV\","
            + "\"distanceKm\":10,\"travelTime\":\"PT1H\"}]}";
    assertThatThrownBy(() -> codec.read(json, Route.class))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("must differ");
  }

  @Test
  void polymorphicLuggageRoundTrip() {
    List<Luggage> luggage =
        List.of(
            new PassengerLuggage(
                "L1", "T1", "KYIV", "RIVNE", 18, 0.1, new BigDecimal("90"),
                LuggageStatus.LOADED, "TK-1"),
            new Shipment(
                "S1", "T1", "KYIV", "RIVNE", 18, 0.2, new BigDecimal("320"),
                new Contact("Olena", null), new Contact("Taras", "+380671234567")));
    String json = codec.write(luggage, LUGGAGE_LIST);
    assertThat(json).contains("\"kind\" : \"PASSENGER\"");
    assertThat(json).contains("\"kind\" : \"SHIPMENT\"");

    List<Luggage> back = codec.read(json, LUGGAGE_LIST);
    assertThat(back).hasSize(2);
    PassengerLuggage first = (PassengerLuggage) back.get(0);
    assertThat(first.getTicketNumber()).isEqualTo("TK-1");
    assertThat(first.getStatus()).isEqualTo(LuggageStatus.LOADED);
    assertThat(first.getPrice()).isEqualByComparingTo(new BigDecimal("90"));
    Shipment second = (Shipment) back.get(1);
    assertThat(second.getRecipient()).isEqualTo(new Contact("Taras", "+380671234567"));
    assertThat(second.getSender().phone()).isNull();
  }

  @Test
  void singleLuggageCarriesKind() {
    Luggage shipment =
        new Shipment("S1", "T1", "KYIV", "RIVNE", 5, 0, BigDecimal.TEN,
            new Contact("A", null), new Contact("B", null));
    String json = codec.write(shipment);
    assertThat(json).contains("\"kind\" : \"SHIPMENT\"");
    assertThat(codec.read(json, Luggage.class)).isInstanceOf(Shipment.class);
  }

  @Test
  void luggageWithoutKindFails() {
    String json = "{\"id\":\"L1\",\"tripId\":\"T1\"}";
    assertThatThrownBy(() -> codec.read(json, Luggage.class))
        .isInstanceOf(StorageException.class);
  }

  @Test
  void busRoundTrip() {
    Bus bus =
        new Bus(
            "AA1234", "Setra S 415", 52, 1500, "KYIV", BusStatus.MAINTENANCE,
            List.of(
                new TimeWindow(
                    LocalDateTime.of(2026, 9, 23, 8, 0), LocalDateTime.of(2026, 9, 24, 8, 0))));
    Bus back = codec.read(codec.write(bus), Bus.class);
    assertThat(back.getDepotCityId()).isEqualTo("KYIV");
    assertThat(back.getStatus()).isEqualTo(BusStatus.MAINTENANCE);
    assertThat(back.getUnavailablePeriods()).isEqualTo(bus.getUnavailablePeriods());
    assertThat(back.getLuggageCapacityKg()).isEqualTo(1500.0);
  }

  @Test
  void scheduleTemplateRoundTrip() {
    ScheduleTemplate template =
        new ScheduleTemplate(
            "ST12", 12, List.of(LocalTime.of(8, 0), LocalTime.of(14, 30)),
            Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), Duration.ofMinutes(30), 2, 35, 400);
    String json = codec.write(template);
    assertThat(json).contains("\"08:00:00\"");
    assertThat(codec.read(json, ScheduleTemplate.class)).isEqualTo(template);
  }

  @Test
  void unknownFieldIsRejected() {
    String json = "{\"id\":\"KYIV\",\"name\":\"Kyiv\",\"latitude\":50,\"longitude\":30,\"lattitude\":1}";
    assertThatThrownBy(() -> codec.read(json, City.class))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("lattitude");
  }

  @Test
  void brokenJsonIsStorageException() {
    assertThatThrownBy(() -> codec.read("{\"id\": \"KYIV\", ", City.class))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("City");
  }

  @Test
  void fileRoundTrip() throws Exception {
    Path file = dir.resolve("data").resolve("cities.json");
    codec.writeFile(file, CITIES, CITY_LIST);
    List<City> back = codec.readFile(file, CITY_LIST);
    assertThat(back).isEqualTo(CITIES);
    try (var files = Files.list(file.getParent())) {
      assertThat(files.toList()).containsExactly(file);
    }
  }

  @Test
  void missingFileIsStorageException() {
    assertThatThrownBy(
            () -> codec.readFile(dir.resolve("nope.json"), CITY_LIST))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("nope.json");
  }
}
