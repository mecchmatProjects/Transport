package transport.model;

import transport.util.DistanceCalculator;

/**
 * Місто — вершина транспортної мережі (README §3). Сполучення між містами зберігаються не тут, а в
 * маршрутах ({@link Route}) і графі міст ({@code util.CityGraph}).
 *
 * @param id унікальний ідентифікатор, напр. {@code "KYIV"}
 * @param name назва для відображення
 * @param latitude широта, градуси [-90; 90]
 * @param longitude довгота, градуси [-180; 180]
 */
public record City(String id, String name, double latitude, double longitude) {

  public City {
    Checks.requireText(id, "id");
    Checks.requireText(name, "name");
    if (!(latitude >= -90 && latitude <= 90)) {
      throw new IllegalArgumentException("latitude out of range: " + latitude);
    }
    if (!(longitude >= -180 && longitude <= 180)) {
      throw new IllegalArgumentException("longitude out of range: " + longitude);
    }
  }

  /** Відстань по великому колу до іншого міста, км. */
  public double distanceKmTo(City other) {
    return DistanceCalculator.haversineKm(latitude, longitude, other.latitude, other.longitude);
  }
}
