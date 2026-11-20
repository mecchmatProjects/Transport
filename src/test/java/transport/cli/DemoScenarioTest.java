package transport.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoScenarioTest {

  private static final Path DATA = Path.of("data");

  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
  private final PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

  @Test
  void allCasesPassOnSeedData() {
    boolean ok = new DemoScenario(DATA, out).run();

    String text = output();
    assertThat(ok).isTrue();
    assertThat(text).contains("Result: 8/8 cases OK");
    assertThat(text).doesNotContain("FAIL");
    for (int n = 1; n <= 8; n++) {
      assertThat(text).containsPattern("(?m)^" + n + " .* OK$");
    }
  }

  @Test
  void dataFilesStayUnchanged() throws IOException {
    Map<Path, byte[]> before = snapshot(DATA);

    new DemoScenario(DATA, out).run();

    Map<Path, byte[]> after = snapshot(DATA);
    assertThat(after.keySet()).isEqualTo(before.keySet());
    before.forEach((file, bytes) ->
        assertThat(after.get(file)).as(file.toString()).isEqualTo(bytes));
  }

  @Test
  void missingEdgeErrorFailsTheScenario(@TempDir Path tmp) throws IOException {
    copy(DATA, tmp);
    // Без маршрутів з ODESA випадок 1 не має що відкидати
    Files.delete(tmp.resolve("edge/unknown-city/routes.json"));

    boolean ok = new DemoScenario(tmp, out).run();

    String text = output();
    assertThat(ok).isFalse();
    assertThat(text).containsPattern("(?m)^1 .* FAIL$");
    assertThat(text).contains("Result: 7/8 cases OK");
  }

  private String output() {
    return buffer.toString(StandardCharsets.UTF_8);
  }

  private static Map<Path, byte[]> snapshot(Path root) throws IOException {
    Map<Path, byte[]> files = new TreeMap<>();
    try (Stream<Path> walk = Files.walk(root)) {
      walk.filter(Files::isRegularFile).forEach(f -> files.put(root.relativize(f), read(f)));
    }
    return files;
  }

  private static void copy(Path from, Path to) throws IOException {
    try (Stream<Path> walk = Files.walk(from)) {
      for (Path source : (Iterable<Path>) walk::iterator) {
        Path target = to.resolve(from.relativize(source).toString());
        if (Files.isDirectory(source)) {
          Files.createDirectories(target);
        } else {
          Files.copy(source, target);
        }
      }
    }
  }

  private static byte[] read(Path file) {
    try {
      return Files.readAllBytes(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
