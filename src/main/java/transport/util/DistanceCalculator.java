package transport.util;

/** Great-circle distance between two points. */
public final class DistanceCalculator {

  private static final double EARTH_RADIUS_KM = 6371.0088;

  private DistanceCalculator() {}

  /**
   * Haversine distance in kilometres.
   *
   * @param lat1 latitude of the first point, degrees
   * @param lon1 longitude of the first point, degrees
   * @param lat2 latitude of the second point, degrees
   * @param lon2 longitude of the second point, degrees
   * @return distance in km
   */
  public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.pow(Math.sin(dLat / 2), 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLon / 2), 2);
    return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
  }
}
