package transport.repository;

import transport.model.Tariff;

/** {@code routeNumber == null} — загальний тариф на напрям. */
public record TariffKey(String fromCityId, String toCityId, Integer routeNumber) {

  public static TariffKey of(Tariff tariff) {
    return new TariffKey(tariff.fromCityId(), tariff.toCityId(), tariff.routeNumber());
  }
}
