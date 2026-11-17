package transport.repository.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import transport.exception.StorageException;
import transport.repository.InMemoryRepository;
import transport.repository.LoadError;
import transport.repository.LoadResult;

/**
 * Змінену сутність треба знову передати в {@code save}, інакше файл не оновиться.
 *
 * <p>Відкат при збої запису гарантований лише для вставки й видалення: змінений на місці об'єкт
 * ({@code Trip}, {@code Ticket}, {@code Bus}) лишається зміненим.
 */
public final class JsonRepository<T, ID> extends InMemoryRepository<T, ID> {

  private final Path file;
  private final JsonCodec codec;
  private final TypeReference<List<T>> listType;
  private LoadResult<T> loadResult = new LoadResult<>(List.of(), List.of());

  public JsonRepository(
      Path file,
      JsonCodec codec,
      TypeReference<List<T>> listType,
      String entityName,
      Function<? super T, ? extends ID> idFunction) {
    super(entityName, idFunction);
    this.file = Objects.requireNonNull(file, "file");
    this.codec = Objects.requireNonNull(codec, "codec");
    this.listType = Objects.requireNonNull(listType, "listType");
    load();
  }

  public Path file() {
    return file;
  }

  /** Прийняті й відкинуті записи; відкинуті зникнуть з файлу після наступного save. */
  public LoadResult<T> loadResult() {
    return loadResult;
  }

  @Override
  public T save(T entity) {
    ID id = idOf(entity);
    Optional<T> previous = findById(id);
    super.save(entity);
    try {
      flush();
    } catch (StorageException e) {
      // запис не вдався — відкочуємо пам'ять до стану файлу
      previous.ifPresentOrElse(super::save, () -> super.deleteById(id));
      throw e;
    }
    return entity;
  }

  @Override
  public boolean deleteById(ID id) {
    Optional<T> previous = findById(id);
    if (previous.isEmpty()) {
      return false;
    }
    super.deleteById(id);
    try {
      flush();
    } catch (StorageException e) {
      super.save(previous.get());
      throw e;
    }
    return true;
  }

  public void flush() {
    codec.writeFile(file, findAll(), listType);
  }

  private void load() {
    if (!Files.exists(file)) {
      return;
    }
    List<JsonNode> records = codec.readArray(file);
    List<T> items = new ArrayList<>();
    List<LoadError> errors = new ArrayList<>();
    for (int i = 0; i < records.size(); i++) {
      int record = i + 1;
      JsonNode node = records.get(i);
      if (node.isNull()) {
        errors.add(error(record, "null entry"));
        continue;
      }
      try {
        T entity = codec.readElement(node, listType);
        ID id = idOf(entity);
        if (existsById(id)) {
          errors.add(error(record, "duplicate " + entityName() + " id " + id
              + ", the first record is kept"));
          continue;
        }
        super.save(entity);
        items.add(entity);
      } catch (StorageException e) {
        errors.add(error(record, e.getMessage()));
      }
    }
    loadResult = new LoadResult<>(items, errors);
  }

  private LoadError error(int record, String message) {
    return new LoadError(file.getFileName().toString(), record, message);
  }
}
