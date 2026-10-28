package transport.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/** Перевірки інваріантів для конструкторів моделі. Кидають {@link IllegalArgumentException}. */
final class Checks {

  private Checks() {}

  static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  static int requirePositive(int value, String field) {
    if (value <= 0) {
      throw new IllegalArgumentException(field + " must be > 0, got " + value);
    }
    return value;
  }

  static double requirePositive(double value, String field) {
    if (!(value > 0) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(field + " must be > 0, got " + value);
    }
    return value;
  }

  static double requireNonNegative(double value, String field) {
    if (!(value >= 0) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(field + " must be >= 0, got " + value);
    }
    return value;
  }

  static BigDecimal requireNonNegative(BigDecimal value, String field) {
    Objects.requireNonNull(value, field);
    if (value.signum() < 0) {
      throw new IllegalArgumentException(field + " must be >= 0, got " + value);
    }
    return value;
  }

  static Duration requirePositive(Duration value, String field) {
    Objects.requireNonNull(value, field);
    if (value.isNegative() || value.isZero()) {
      throw new IllegalArgumentException(field + " must be positive, got " + value);
    }
    return value;
  }

  static Duration requireNonNegative(Duration value, String field) {
    Objects.requireNonNull(value, field);
    if (value.isNegative()) {
      throw new IllegalArgumentException(field + " must not be negative, got " + value);
    }
    return value;
  }

  static void requireDifferent(String fromCityId, String toCityId) {
    if (fromCityId.equals(toCityId)) {
      throw new IllegalArgumentException("from and to must differ: " + fromCityId);
    }
  }
}
