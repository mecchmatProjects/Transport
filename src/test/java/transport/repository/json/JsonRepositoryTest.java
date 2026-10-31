package transport.repository.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import transport.exception.StorageException;
import transport.model.City;

class JsonRepositoryTest {

  private static final TypeReference<List<City>> CITY_LIST = new TypeReference<>() {};

  private static final City KYIV = new City("KYIV", "Kyiv", 50.4501, 30.5234);
  private static final City LVIV = new City("LVIV", "Lviv", 49.8397, 24.0297);

  @TempDir Path dir;

  private JsonRepository<City, String> open(Path file) {
    return new JsonRepository<>(file, new JsonCodec(), CITY_LIST, "City", City::id);
  }

  @Test
  void missingFileIsEmptyAndAppearsOnFirstSave() {
    Path file = dir.resolve("cities.json");
    JsonRepository<City, String> repo = open(file);
    assertThat(repo.count()).isEqualTo(0L);
    assertThat(Files.exists(file)).isFalse();

    repo.add(KYIV);
    assertThat(Files.exists(file)).isTrue();
  }

  @Test
  void saveAndDeleteSurviveReopen() {
    Path file = dir.resolve("cities.json");
    JsonRepository<City, String> repo = open(file);
    repo.add(KYIV);
    repo.add(LVIV);
    repo.deleteById("KYIV");

    assertThat(open(file).findAll()).containsExactly(LVIV);
  }

  @Test
  void duplicateIdInFileFails() throws Exception {
    Path file = dir.resolve("cities.json");
    String city = new JsonCodec().write(KYIV);
    Files.writeString(file, "[" + city + "," + city + "]");

    assertThatThrownBy(() -> open(file))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("duplicate City id KYIV");
  }

  @Test
  void failedWriteLeavesMemoryUnchanged() throws Exception {
    Path file = dir.resolve("cities.json");
    JsonRepository<City, String> repo = open(file);
    repo.add(KYIV);

    // на місці файлу — непорожня тека: перейменування тимчасового файлу впаде
    Files.delete(file);
    Files.createDirectories(file.resolve("blocker"));

    assertThatThrownBy(() -> repo.add(LVIV)).isInstanceOf(StorageException.class);
    assertThatThrownBy(() -> repo.deleteById("KYIV")).isInstanceOf(StorageException.class);
    assertThat(repo.findAll()).containsExactly(KYIV);
  }
}
