package transport.repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import transport.exception.DuplicateEntityException;
import transport.exception.EntityNotFoundException;

/**
 * Сховище сутностей одного типу. Сервіси працюють лише через цей інтерфейс, тож реалізацію
 * (у пам'яті, JSON-файли, у сценарії C — БД) можна замінити без змін у сервісах.
 *
 * <p>Реалізації: {@code InMemoryRepository}, {@code JsonRepository} (задача K1).
 *
 * @param <T> тип сутності
 * @param <ID> тип ідентифікатора
 */
public interface Repository<T, ID> {

  /** Ідентифікатор сутності (напр. {@code Bus::getId}, {@code Route::number}). */
  ID idOf(T entity);

  /** Назва типу для повідомлень про помилки, напр. {@code "Bus"}. */
  String entityName();

  Optional<T> findById(ID id);

  /** Усі сутності в порядку додавання. */
  List<T> findAll();

  /** Вставляє нову або замінює наявну сутність з тим самим id. */
  T save(T entity);

  /** @return {@code true}, якщо сутність була і її видалено */
  boolean deleteById(ID id);

  long count();

  /**
   * @throws EntityNotFoundException якщо сутності немає
   */
  default T getById(ID id) {
    return findById(id).orElseThrow(() -> new EntityNotFoundException(entityName(), id));
  }

  default boolean existsById(ID id) {
    return findById(id).isPresent();
  }

  /**
   * Додає лише нову сутність.
   *
   * @throws DuplicateEntityException якщо сутність з таким id уже є
   */
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
