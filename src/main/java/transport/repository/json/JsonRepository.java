package transport.repository.json;

import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import transport.exception.StorageException;
import transport.repository.InMemoryRepository;

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
    List<T> entities = codec.readFile(file, listType);
    if (entities == null) {
      return; // файл із вмістом null
    }
    for (T entity : entities) {
      if (entity == null) {
        throw new StorageException(file + ": null entry", null);
      }
      ID id = idOf(entity);
      if (existsById(id)) {
        throw new StorageException(file + ": duplicate " + entityName() + " id " + id, null);
      }
      super.save(entity);
    }
  }
}
