package transport.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import transport.exception.StorageException;
import transport.repository.json.JsonCodec;

/** Налаштування з {@code data/config.json}; правка файлу не потребує перекомпіляції. */
public record AppConfig(TariffConfig tariff, int maxRoutes) {

  public static final String FILE_NAME = "config.json";

  public static final AppConfig DEFAULT =
      new AppConfig(TariffConfig.DEFAULT, 30);

  public AppConfig {
    Objects.requireNonNull(tariff, "tariff");
    if (maxRoutes <= 0) {
      throw new IllegalArgumentException("maxRoutes must be > 0, got " + maxRoutes);
    }
  }

  /** Читає {@code config.json} з теки даних; якщо файлу немає, повертає {@link #DEFAULT}. */
  public static AppConfig load(Path dataDir) {
    Path file = dataDir.resolve(FILE_NAME);
    if (!Files.exists(file)) {
      return DEFAULT;
    }
    try {
      return new JsonCodec().read(Files.readString(file), AppConfig.class);
    } catch (IOException e) {
      throw new StorageException("cannot read " + file, e);
    }
  }
}
