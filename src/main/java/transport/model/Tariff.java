package transport.model;

import java.math.BigDecimal;

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

  public static Tariff general(String from, String to, BigDecimal fare, BigDecimal luggageRatePerKg) {
    return new Tariff(from, to, null, fare, luggageRatePerKg);
  }

  public boolean appliesTo(int route, String from, String to) {
    return fromCityId.equals(from)
        && toCityId.equals(to)
        && (routeNumber == null || routeNumber == route);
  }

  /** Тариф конкретного маршруту має пріоритет над загальним. */
  public boolean isRouteSpecific() {
    return routeNumber != null;
  }

  public BigDecimal luggagePrice(double weightKg) {
    Checks.requirePositive(weightKg, "weightKg");
    return luggageRatePerKg.multiply(BigDecimal.valueOf(weightKg));
  }
}
