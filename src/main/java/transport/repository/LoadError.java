package transport.repository;

import java.util.Objects;

/** Відкинутий при завантаженні запис: файл, номер запису з 1 і причина. */
public record LoadError(String file, int record, String message) {

  public LoadError {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(message, "message");
  }

  @Override
  public String toString() {
    return file + ", record " + record + ": " + message;
  }
}
