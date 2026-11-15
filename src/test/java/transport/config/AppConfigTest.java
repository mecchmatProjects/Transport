package transport.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import transport.exception.StorageException;

class AppConfigTest {

  @TempDir Path dir;

  @Test
  void missingFileGivesDefaults() {
    assertThat(AppConfig.load(dir)).isEqualTo(AppConfig.DEFAULT);
  }

  @Test
  void seedConfigMatchesDefaults() {
    AppConfig seed = AppConfig.load(Path.of("data"));

    assertThat(seed.maxRoutes()).isEqualTo(30);
    assertThat(seed.tariff().baseFare().compareTo(new BigDecimal("50"))).isEqualTo(0);
  }

  @Test
  void typoInKeyIsReported() throws Exception {
    Files.writeString(dir.resolve(AppConfig.FILE_NAME), """
        {"tariff": {"baseFare": 50, "farePerKm": 1.2, "luggagePerKgPerKm": 0.01,
                    "priceScale": 0},
         "maxRoute": 30}
        """);

    assertThatThrownBy(() -> AppConfig.load(dir)).isInstanceOf(StorageException.class);
  }

  @Test
  void negativeRateIsRejected() throws Exception {
    Files.writeString(dir.resolve(AppConfig.FILE_NAME), """
        {"tariff": {"baseFare": -1, "farePerKm": 1.2, "luggagePerKgPerKm": 0.01,
                    "priceScale": 0},
         "maxRoutes": 30}
        """);

    assertThatThrownBy(() -> AppConfig.load(dir)).isInstanceOf(StorageException.class);
  }
}
