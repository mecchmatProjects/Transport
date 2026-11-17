package transport.repository.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import transport.exception.StorageException;
import transport.model.Bus;
import transport.model.City;
import transport.model.Luggage;
import transport.model.Route;
import transport.repository.LoadError;

/** Граничні випадки 1–3 на наборах з {@code data/edge/}. */
class EdgeDataTest {

  private static final Path EDGE = Path.of("data", "edge");

  // Випадок 1
  @Test
  void routeWithUnknownCityIsSkipped() {
    JsonStorage storage = JsonStorage.openSkippingInvalid(EDGE.resolve("unknown-city"));

    assertThat(storage.cities().count()).isEqualTo(4L);
    assertThat(storage.routes().findAll()).extracting(Route::number).containsExactly(1, 3);
    assertThat(storage.loadErrors()).singleElement().satisfies(e -> {
      assertThat(e.file()).isEqualTo(JsonStorage.ROUTES);
      assertThat(e.record()).isEqualTo(2);
      assertThat(e.message()).contains("unknown city 'ODESA'");
    });
  }

  // Випадок 2
  @Test
  void invalidValuesAreSkipped() {
    JsonStorage storage = JsonStorage.openSkippingInvalid(EDGE.resolve("invalid-values"));

    assertThat(storage.cities().findAll()).extracting(City::id).containsExactly("KYIV");
    assertThat(storage.buses().findAll()).extracting(Bus::getId).containsExactly("AA1234AB");
    assertThat(storage.luggage().findAll()).extracting(Luggage::getId).containsExactly("L1");
    assertThat(storage.loadErrors()).extracting(LoadError::toString).containsExactly(
        "cities.json, record 2: name must not be blank",
        "buses.json, record 2: seats must be > 0, got 0",
        "buses.json, record 3: luggageCapacityKg must be >= 0, got -100.0",
        "luggage.json, record 2: weightKg must be > 0, got -18.0");
  }

  // Випадок 3
  @Test
  void duplicatesKeepTheFirstRecord() {
    JsonStorage storage = JsonStorage.openSkippingInvalid(EDGE.resolve("duplicates"));

    assertThat(storage.buses().getById("AA1234AB").getModel()).isEqualTo("Setra S 515 HD");
    assertThat(storage.tickets().getById("T1-001").getSeatNumber()).isEqualTo(1);
    assertThat(storage.loadErrors()).extracting(LoadError::file)
        .containsExactly(JsonStorage.BUSES, JsonStorage.TICKETS);
  }

  @Test
  void strictOpenRejectsBadData() {
    assertThatThrownBy(() -> JsonStorage.open(EDGE.resolve("invalid-values")))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("cities.json, record 2: name must not be blank")
        .hasMessageContaining("(+3 more)");
  }

  @Test
  void seedDataHasNoErrors() {
    assertThat(JsonStorage.openSkippingInvalid(Path.of("data")).loadErrors()).isEmpty();
  }
}
