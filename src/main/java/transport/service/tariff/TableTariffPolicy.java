package transport.service.tariff;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;
import transport.model.Route;
import transport.model.Tariff;
import transport.service.TariffService;

/** Ціни з {@code tariffs.json}: спершу тариф маршруту, потім загальний на напрям. */
public class TableTariffPolicy implements TariffPolicy {

  private final TariffService tariffs;
  private final int priceScale;

  public TableTariffPolicy(TariffService tariffs, int priceScale) {
    this.tariffs = Objects.requireNonNull(tariffs, "tariffs");
    this.priceScale = priceScale;
  }

  @Override
  public Optional<BigDecimal> ticketPrice(Route route, String fromCityId, String toCityId) {
    return find(route, fromCityId, toCityId).map(Tariff::fare);
  }

  @Override
  public Optional<BigDecimal> luggagePrice(
      Route route, String fromCityId, String toCityId, double kg) {
    return find(route, fromCityId, toCityId)
        .map(tariff -> tariff.luggagePrice(kg).setScale(priceScale, RoundingMode.HALF_UP));
  }

  private Optional<Tariff> find(Route route, String fromCityId, String toCityId) {
    TariffPolicy.checkLeg(route, fromCityId, toCityId);
    return tariffs.find(route.number(), fromCityId, toCityId);
  }
}
