package transport.service;

import java.util.List;
import java.util.Objects;
import transport.repository.Repository;

/** Спільний CRUD; підкласи задають валідацію і перевірки цілісності через хуки. */
public abstract class AbstractCrudService<T, ID> {

  protected final Repository<T, ID> repository;

  protected AbstractCrudService(Repository<T, ID> repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  /** Додає нову сутність; дубль id кидає {@code DuplicateEntityException}. */
  public T create(T entity) {
    validate(entity);
    beforeCreate(entity);
    return repository.add(entity);
  }

  /** Замінює наявну сутність з тим самим id. */
  public T update(T entity) {
    T existing = repository.getById(repository.idOf(entity));
    validate(entity);
    beforeUpdate(existing, entity);
    return repository.save(entity);
  }

  /** Повертає сутність або кидає {@code EntityNotFoundException}. */
  public T get(ID id) {
    return repository.getById(id);
  }

  /** Усі сутності в порядку додавання. */
  public List<T> list() {
    return repository.findAll();
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

  protected abstract void validate(T entity);

  protected void beforeCreate(T entity) {}

  protected void beforeUpdate(T existing, T updated) {}

  protected void beforeDelete(T entity) {}
}
