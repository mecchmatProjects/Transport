package transport.service;

import java.util.List;
import java.util.Objects;
import transport.repository.Repository;

/** Спільний CRUD; назовні віддаються лише копії збережених сутностей. */

public abstract class AbstractCrudService<T, ID> {

  protected final Repository<T, ID> repository;

  protected AbstractCrudService(Repository<T, ID> repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  /** Додає нову сутність; дубль id кидає {@code DuplicateEntityException}. */
  public T create(T entity) {
    validate(entity);
    beforeCreate(entity);
    repository.add(copy(entity));
    return copy(entity);
  }

  /** Замінює наявну сутність з тим самим id. */
  public T update(T entity) {
    T existing = repository.getById(repository.idOf(entity));
    validate(entity);
    beforeUpdate(existing, entity);
    return store(entity);
  }

  /** Повертає копію сутності або кидає {@code EntityNotFoundException}. */
  public T get(ID id) {
    return copy(repository.getById(id));
  }

  /** Копії всіх сутностей у порядку додавання. */
  public List<T> list() {
    return repository.findAll().stream().map(this::copy).toList();
  }

  /** Кількість сутностей. */
  public long count() {
    return repository.count();
  }

  /** Видаляє сутність, якщо на неї ніщо не посилається. */
  public void delete(ID id) {
    T existing = repository.getById(id);
    beforeDelete(existing);
    repository.deleteById(id);
  }

  /** Зберігає копію сутності й повертає ще одну копію для виклику. */
  protected final T store(T entity) {
    repository.save(copy(entity));
    return copy(entity);
  }

  /** Копія сутності; для незмінних records та сама. */

  protected T copy(T entity) {
    return entity;
  }

  protected abstract void validate(T entity);

  protected void beforeCreate(T entity) {}

  /** {@code existing} це збережений екземпляр: хук його лише читає. */
  protected void beforeUpdate(T existing, T updated) {}

  protected void beforeDelete(T entity) {}
}
