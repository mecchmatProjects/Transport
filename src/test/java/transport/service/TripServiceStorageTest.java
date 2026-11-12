package transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import transport.exception.StorageException;
import transport.model.Bus;
import transport.model.Trip;
import transport.model.TripStatus;
import transport.repository.json.JsonStorage;

class TripServiceStorageTest {

  @TempDir Path dir;

  private JsonStorage storage;
  private TripService service;

  @BeforeEach
  void setUp() {
    storage = JsonStorage.open(dir);
    List.of(ServiceFixture.KYIV, ServiceFixture.ZHYTOMYR, ServiceFixture.RIVNE,
        ServiceFixture.LUTSK).forEach(storage.cities()::add);
    storage.routes().add(ServiceFixture.kyivLutsk(12));
    storage.buses().add(new Bus("AA1234", "Setra", 52, 1500, "KYIV"));
    service = new TripService(storage.trips(), storage.routes(), storage.buses(),
        storage.tickets(), storage.luggage());
    service.create(new Trip("T1", storage.routes().getById(12), ServiceFixture.DATE,
        LocalTime.of(8, 0)));
  }

  // На місці trips.json непорожня тека: запис впаде, а пам'ять має лишитися як у файлі
  @Test
  void failedWriteKeepsMemoryInSyncWithFile() throws Exception {
    Path file = dir.resolve(JsonStorage.TRIPS);
    Files.delete(file);
    Files.createDirectories(file.resolve("blocker"));

    assertThatThrownBy(() -> service.assignBus("T1", "AA1234"))
        .isInstanceOf(StorageException.class);
    assertThat(service.get("T1").getBusId()).isNull();

    assertThatThrownBy(() -> service.cancel("T1")).isInstanceOf(StorageException.class);
    assertThat(service.get("T1").getStatus()).isEqualTo(TripStatus.SCHEDULED);
  }
}
