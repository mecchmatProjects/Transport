package transport.model;

import transport.exception.InvalidStatusTransitionException;

/**
 * Статус зі скінченним автоматом переходів. Реалізують enum-и {@link TripStatus}, {@link
 * TicketStatus}, {@link LuggageStatus}.
 *
 * @param <S> сам enum статусу
 */
public interface Transitional<S extends Enum<S> & Transitional<S>> {

  /** Чи дозволено перейти з цього статусу в {@code next}. */
  boolean canTransitionTo(S next);

  /** Чи є статус кінцевим (з нього немає жодного переходу). */
  default boolean isTerminal() {
    @SuppressWarnings("unchecked")
    S self = (S) this;
    for (S candidate : self.getDeclaringClass().getEnumConstants()) {
      if (canTransitionTo(candidate)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Перевіряє перехід і повертає новий статус.
   *
   * @throws InvalidStatusTransitionException якщо перехід заборонений
   */
  static <S extends Enum<S> & Transitional<S>> S transition(S from, S to) {
    if (!from.canTransitionTo(to)) {
      throw new InvalidStatusTransitionException(from, to);
    }
    return to;
  }
}
