package transport.service.tariff;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;
import transport.config.TariffConfig;
import transport.model.Route;

/** {@code baseFare + farePerKm × км}, багаж {@code luggagePerKgPerKm × кг × км}; ціна є завжди. */
public class DistanceTariffPolicy implements TariffPolicy {

  private final TariffConfig config;

  public DistanceTariffPolicy(TariffConfig config) {
    this.config = Objects.requireNonNull(config, "config");
  }

  @Override
  public Optional<BigDecimal> ticketPrice(Route route, String fromCityId, String toCityId) {
    BigDecimal km = km(route, fromCityId, toCityId);
    return Optional.of(round(config.baseFare().add(config.farePerKm().multiply(km))));
  }

  @Override
  public Optional<BigDecimal> luggagePrice(
      Route route, String fromCityId, String toCityId, double kg) {
    if (!(kg > 0)) {
      throw new IllegalArgumentException("kg must be > 0, got " + kg);
    }
    BigDecimal km = km(route, fromCityId, toCityId);
    return Optional.of(round(config.luggagePerKgPerKm().multiply(BigDecimal.valueOf(kg))
        .multiply(km)));
  }

  private static BigDecimal km(Route route, String fromCityId, String toCityId) {
    TariffPolicy.checkLeg(route, fromCityId, toCityId);
    return BigDecimal.valueOf(route.distanceKm(fromCityId, toCityId));
  }

  private BigDecimal round(BigDecimal price) {
    return price.setScale(config.priceScale(), RoundingMode.HALF_UP);
  }
}
