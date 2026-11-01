package transport.repository.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import transport.model.Bus;
import transport.model.Route;
import transport.model.RouteSegment;
import transport.model.ScheduleTemplate;
import transport.model.Tariff;

class SeedDataTest {

  private static final Path SEED = Path.of("data");

  private final JsonStorage storage = JsonStorage.open(SEED);

  @Test
  void meetsReadmeMinimums() {
    assertThat(storage.cities().count() >= 20).isTrue();
    assertThat(storage.routes().count() >= 5 && storage.routes().count() <= 30).isTrue();
    assertThat(storage.buses().count() > 0).isTrue();
    assertThat(storage.tariffs().count() > 0).isTrue();
    assertThat(storage.scheduleTemplates().count() > 0).isTrue();
  }

  @Test
  void openingDoesNotWriteRuntimeFiles() {
    assertThat(Files.exists(SEED.resolve(JsonStorage.TRIPS))).isFalse();
    assertThat(Files.exists(SEED.resolve(JsonStorage.TICKETS))).isFalse();
    assertThat(Files.exists(SEED.resolve(JsonStorage.LUGGAGE))).isFalse();
  }

  @Test
  void roadDistanceIsNotShorterThanStraightLine() {
    for (Route route : storage.routes().findAll()) {
      for (RouteSegment s : route.segments()) {
        assertThat(s.distanceKm() >= s.from().distanceKmTo(s.to())).as(s.toString()).isTrue();
      }
    }
  }

  @Test
  void busDepotsExist() {
    for (Bus bus : storage.buses().findAll()) {
      assertThat(storage.cities().existsById(bus.getDepotCityId())).as(bus.getId()).isTrue();
    }
  }

  @Test
  void templatesReferenceExistingRoutes() {
    for (ScheduleTemplate t : storage.scheduleTemplates().findAll()) {
      assertThat(storage.routes().existsById(t.routeNumber())).as(t.id()).isTrue();
    }
  }

  @Test
  void everyTariffIsServedByARoute() {
    for (Tariff t : storage.tariffs().findAll()) {
      boolean served =
          storage.routes().findAll().stream()
              .filter(r -> t.routeNumber() == null || r.number() == t.routeNumber())
              .anyMatch(r -> r.servesDirection(t.fromCityId(), t.toCityId()));
      assertThat(served).as(t.toString()).isTrue();
    }
  }
}
