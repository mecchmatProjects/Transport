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
 * {@link InMemoryRepository}, що тримає копію у JSON-файлі: читає його при створенні і
 * переписує цілком після кожного {@code save} / {@code deleteById}.
 *
 * <p>Змінні сутності ({@code Trip}, {@code Ticket}, {@code Bus}) після зміни треба знову передати в
 * {@code save}, інакше зміна не потрапить у файл.
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
      // файл не записався — повертаємо пам'ять у стан, що збігається з файлом
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

  /** Записує поточний вміст у файл. */
  public void flush() {
    codec.writeFile(file, findAll(), listType);
  }

  private void load() {
    if (!Files.exists(file)) {
      return; // файл з'явиться при першому save
    }
    for (T entity : codec.readFile(file, listType)) {
      ID id = idOf(entity);
      if (existsById(id)) {
        throw new StorageException(file + ": duplicate " + entityName() + " id " + id, null);
      }
      super.save(entity);
    }
  }
}
