package transport.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Сховище в пам'яті; порядок {@link #findAll()} — порядок першого додавання. */
public class InMemoryRepository<T, ID> implements Repository<T, ID> {

  private final String entityName;
  private final Function<? super T, ? extends ID> idFunction;
  private final Map<ID, T> data = new LinkedHashMap<>();

  public InMemoryRepository(String entityName, Function<? super T, ? extends ID> idFunction) {
    this.entityName = Objects.requireNonNull(entityName, "entityName");
    this.idFunction = Objects.requireNonNull(idFunction, "idFunction");
  }

  @Override
  public ID idOf(T entity) {
    Objects.requireNonNull(entity, entityName);
    return Objects.requireNonNull(idFunction.apply(entity), entityName + " id");
  }

  @Override
  public String entityName() {
    return entityName;
  }

  @Override
  public Optional<T> findById(ID id) {
    return Optional.ofNullable(data.get(id));
  }

  @Override
  public List<T> findAll() {
    return List.copyOf(data.values());
  }

  @Override
  public T save(T entity) {
    data.put(idOf(entity), entity); // заміна лишає сутність на її місці в порядку
    return entity;
  }

  @Override
  public boolean deleteById(ID id) {
    return data.remove(id) != null;
  }

  @Override
  public long count() {
    return data.size();
  }
}
