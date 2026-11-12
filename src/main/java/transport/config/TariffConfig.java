package transport.config;

import java.math.BigDecimal;
import java.util.Objects;

/** Коефіцієнти тарифу за відстанню; ціни округлюються до {@code priceScale} знаків. */
public record TariffConfig(
    BigDecimal baseFare, BigDecimal farePerKm, BigDecimal luggagePerKgPerKm, int priceScale) {

  public static final TariffConfig DEFAULT = new TariffConfig(
      new BigDecimal("50"), new BigDecimal("1.2"), new BigDecimal("0.01"), 0);

  public TariffConfig {
    requireNonNegative(baseFare, "baseFare");
    requireNonNegative(farePerKm, "farePerKm");
    requireNonNegative(luggagePerKgPerKm, "luggagePerKgPerKm");
    if (priceScale < 0 || priceScale > 4) {
      throw new IllegalArgumentException("priceScale must be in 0..4, got " + priceScale);
    }
  }

  private static void requireNonNegative(BigDecimal value, String field) {
    Objects.requireNonNull(value, field);
    if (value.signum() < 0) {
      throw new IllegalArgumentException(field + " must be >= 0, got " + value);
    }
  }
}
