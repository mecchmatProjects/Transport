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
import transport.repository.LoadError;

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
  void duplicateIdInFileKeepsFirstRecord() throws Exception {
    Path file = dir.resolve("cities.json");
    City renamed = new City("KYIV", "Kiev", 50.4501, 30.5234);
    JsonCodec codec = new JsonCodec();
    Files.writeString(file, "[" + codec.write(KYIV) + "," + codec.write(renamed) + "]");

    JsonRepository<City, String> repo = open(file);

    assertThat(repo.findAll()).containsExactly(KYIV);
    assertThat(repo.findById("KYIV").orElseThrow().name()).isEqualTo("Kyiv");
    assertThat(repo.loadResult().errors()).singleElement()
        .hasToString("cities.json, record 2: duplicate City id KYIV, the first record is kept");
  }

  @Test
  void nullFileIsEmpty() throws Exception {
    Path file = dir.resolve("cities.json");
    Files.writeString(file, "null");
    assertThat(open(file).count()).isEqualTo(0L);
  }

  @Test
  void nullEntryIsReported() throws Exception {
    Path file = dir.resolve("cities.json");
    Files.writeString(file, "[null, " + new JsonCodec().write(LVIV) + "]");

    JsonRepository<City, String> repo = open(file);

    assertThat(repo.findAll()).containsExactly(LVIV);
    assertThat(repo.loadResult().errors()).extracting(LoadError::record).containsExactly(1);
  }

  @Test
  void invalidRecordIsSkippedAndRestLoads() throws Exception {
    Path file = dir.resolve("cities.json");
    String blankName = "{\"id\":\"ODESA\",\"name\":\"\",\"latitude\":46.48,\"longitude\":30.72}";
    Files.writeString(file, "[" + new JsonCodec().write(KYIV) + "," + blankName + "]");

    JsonRepository<City, String> repo = open(file);

    assertThat(repo.findAll()).containsExactly(KYIV);
    assertThat(repo.loadResult().items()).containsExactly(KYIV);
    assertThat(repo.loadResult().errors()).singleElement()
        .hasToString("cities.json, record 2: name must not be blank");
  }

  @Test
  void nonArrayFileFails() throws Exception {
    Path file = dir.resolve("cities.json");
    Files.writeString(file, "{}");
    assertThatThrownBy(() -> open(file))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("expected a JSON array");
  }

  @Test
  void emptyFileFails() throws Exception {
    Path file = dir.resolve("cities.json");
    Files.writeString(file, "");
    assertThatThrownBy(() -> open(file))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("cities.json");
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
    try (var files = Files.list(dir)) {
      assertThat(files.noneMatch(f -> f.toString().endsWith(".tmp"))).isTrue();
    }
  }
}
