package transport.model;

import java.math.BigDecimal;

/**
 * Тариф між двома містами (README §5). Тарифи зберігаються в даних, а не в коді, тож їх можна
 * змінювати без перекомпіляції.
 *
 * @param fromCityId місто відправлення
 * @param toCityId місто прибуття
 * @param routeNumber номер маршруту, якщо тариф діє лише на ньому; {@code null} — на будь-якому
 * @param fare вартість пасажирського квитка
 * @param luggageRatePerKg вартість перевезення 1 кг багажу
 */
public record Tariff(
    String fromCityId,
    String toCityId,
    Integer routeNumber,
    BigDecimal fare,
    BigDecimal luggageRatePerKg) {

  public Tariff {
    Checks.requireText(fromCityId, "fromCityId");
    Checks.requireText(toCityId, "toCityId");
    Checks.requireDifferent(fromCityId, toCityId);
    if (routeNumber != null) {
      Checks.requirePositive(routeNumber, "routeNumber");
    }
    Checks.requireNonNegative(fare, "fare");
    Checks.requireNonNegative(luggageRatePerKg, "luggageRatePerKg");
  }

  /** Тариф, що діє на будь-якому маршруті. */
  public static Tariff general(String from, String to, BigDecimal fare, BigDecimal luggageRatePerKg) {
    return new Tariff(from, to, null, fare, luggageRatePerKg);
  }

  /** Чи стосується тариф маршруту та пари міст. */
  public boolean appliesTo(int route, String from, String to) {
    return fromCityId.equals(from)
        && toCityId.equals(to)
        && (routeNumber == null || routeNumber == route);
  }

  /** Чи прив'язаний тариф до конкретного маршруту (має пріоритет над загальним). */
  public boolean isRouteSpecific() {
    return routeNumber != null;
  }

  /** Вартість перевезення багажу заданої ваги. */
  public BigDecimal luggagePrice(double weightKg) {
    Checks.requirePositive(weightKg, "weightKg");
    return luggageRatePerKg.multiply(BigDecimal.valueOf(weightKg));
  }
}
