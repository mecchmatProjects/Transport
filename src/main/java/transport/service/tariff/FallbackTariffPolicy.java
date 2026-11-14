package transport.service.tariff;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import transport.config.TariffConfig;
import transport.model.Route;
import transport.service.TariffService;

/** Chain of Responsibility: якщо {@code primary} ціни не знає, питаємо {@code fallback}. */
public class FallbackTariffPolicy implements TariffPolicy {

  private final TariffPolicy primary;
  private final TariffPolicy fallback;

  public FallbackTariffPolicy(TariffPolicy primary, TariffPolicy fallback) {
    this.primary = Objects.requireNonNull(primary, "primary");
    this.fallback = Objects.requireNonNull(fallback, "fallback");
  }

  /** Стандартний ланцюжок: таблиця тарифів, потім ціна за відстанню. */
  public static FallbackTariffPolicy tableThenDistance(TariffService tariffs, TariffConfig config) {
    return new FallbackTariffPolicy(
        new TableTariffPolicy(tariffs, config.priceScale()), new DistanceTariffPolicy(config));
  }

  @Override
  public Optional<BigDecimal> ticketPrice(Route route, String fromCityId, String toCityId) {
    return primary.ticketPrice(route, fromCityId, toCityId)
        .or(() -> fallback.ticketPrice(route, fromCityId, toCityId));
  }

  @Override
  public Optional<BigDecimal> luggagePrice(
      Route route, String fromCityId, String toCityId, double kg) {
    return primary.luggagePrice(route, fromCityId, toCityId, kg)
        .or(() -> fallback.luggagePrice(route, fromCityId, toCityId, kg));
  }
}
