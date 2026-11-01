package transport.model;

import transport.util.DistanceCalculator;

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

  public double distanceKmTo(City other) {
    return DistanceCalculator.haversineKm(latitude, longitude, other.latitude, other.longitude);
  }
}
