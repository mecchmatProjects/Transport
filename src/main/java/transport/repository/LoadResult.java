package transport.repository;

import java.util.List;

/** Результат завантаження: прийняті записи й помилки відкинутих. */
public record LoadResult<T>(List<T> items, List<LoadError> errors) {

  public LoadResult {
    items = List.copyOf(items);
    errors = List.copyOf(errors);
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }
}
