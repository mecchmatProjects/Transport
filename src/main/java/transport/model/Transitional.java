package transport.model;

import transport.exception.InvalidStatusTransitionException;

public interface Transitional<S extends Enum<S> & Transitional<S>> {

  boolean canTransitionTo(S next);

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

  static <S extends Enum<S> & Transitional<S>> S transition(S from, S to) {
    if (!from.canTransitionTo(to)) {
      throw new InvalidStatusTransitionException(from, to);
    }
    return to;
  }
}
