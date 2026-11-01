package transport.repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import transport.exception.DuplicateEntityException;
import transport.exception.EntityNotFoundException;

public interface Repository<T, ID> {

  ID idOf(T entity);

  String entityName();

  Optional<T> findById(ID id);

  List<T> findAll();

  T save(T entity);

  boolean deleteById(ID id);

  long count();

  default T getById(ID id) {
    return findById(id).orElseThrow(() -> new EntityNotFoundException(entityName(), id));
  }

  default boolean existsById(ID id) {
    return findById(id).isPresent();
  }

  /** На відміну від {@link #save}, не замінює наявну сутність. */
  default T add(T entity) {
    ID id = idOf(entity);
    if (existsById(id)) {
      throw new DuplicateEntityException(entityName(), id);
    }
    return save(entity);
  }

  default List<T> findAll(Predicate<? super T> filter) {
    return findAll().stream().filter(filter).toList();
  }
}
