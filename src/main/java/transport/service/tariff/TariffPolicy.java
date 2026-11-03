package transport.service.tariff;

import java.math.BigDecimal;
import java.util.Optional;
import transport.exception.ValidationException;
import transport.model.Route;

/** Стратегія ціноутворення; порожній результат означає «ця політика ціни не знає». */
public interface TariffPolicy {

  /** Ціна квитка між двома зупинками маршруту. */
  Optional<BigDecimal> ticketPrice(Route route, String fromCityId, String toCityId);

  /** Ціна перевезення {@code kg} багажу між двома зупинками маршруту. */
  Optional<BigDecimal> luggagePrice(Route route, String fromCityId, String toCityId, double kg);

  /** Як {@link #ticketPrice}, але без ціни кидає {@code ValidationException}. */
  default BigDecimal requireTicketPrice(Route route, String fromCityId, String toCityId) {
    return ticketPrice(route, fromCityId, toCityId)
        .orElseThrow(() -> noPrice(route, fromCityId, toCityId));
  }

  /** Як {@link #luggagePrice}, але без ціни кидає {@code ValidationException}. */
  default BigDecimal requireLuggagePrice(
      Route route, String fromCityId, String toCityId, double kg) {
    return luggagePrice(route, fromCityId, toCityId, kg)
        .orElseThrow(() -> noPrice(route, fromCityId, toCityId));
  }

  static void checkLeg(Route route, String fromCityId, String toCityId) {
    if (!route.servesDirection(fromCityId, toCityId)) {
      throw new ValidationException(
          "route " + route.number() + " does not go " + fromCityId + " -> " + toCityId);
    }
  }

  private static ValidationException noPrice(Route route, String from, String to) {
    return new ValidationException(
        "no tariff for route " + route.number() + ": " + from + " -> " + to);
  }
}
