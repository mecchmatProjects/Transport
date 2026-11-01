package transport.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Зупинки нумеруються з 0; ділянка {@code i} з'єднує зупинки {@code i} та {@code i + 1}. */
public record Route(int number, String name, List<RouteSegment> segments) {

  public Route {
    Checks.requirePositive(number, "number");
    Checks.requireText(name, "name");
    Objects.requireNonNull(segments, "segments");
    if (segments.isEmpty()) {
      throw new IllegalArgumentException("route " + number + " must have at least one segment");
    }
    segments = List.copyOf(segments);
    if (!segments.get(segments.size() - 1).dwellAtEnd().isZero()) {
      throw new IllegalArgumentException("route " + number + ": no dwell at the final stop");
    }

    Set<String> seen = new HashSet<>();
    seen.add(segments.get(0).from().id());
    for (int i = 0; i < segments.size(); i++) {
      RouteSegment segment = segments.get(i);
      if (i > 0 && !segments.get(i - 1).to().id().equals(segment.from().id())) {
        throw new IllegalArgumentException("route " + number + ": segment " + i + " is not connected");
      }
      if (!seen.add(segment.to().id())) {
        throw new IllegalArgumentException(
            "route " + number + ": city visited twice: " + segment.to().id());
      }
    }
  }

  public static Route withUniformDwell(
      int number, String name, List<RouteSegment> segments, Duration dwell) {
    List<RouteSegment> result = new ArrayList<>(segments.size());
    for (int i = 0; i < segments.size(); i++) {
      boolean last = i == segments.size() - 1;
      result.add(segments.get(i).withDwellAtEnd(last ? Duration.ZERO : dwell));
    }
    return new Route(number, name, result);
  }

  public List<City> stops() {
    List<City> stops = new ArrayList<>(segments.size() + 1);
    stops.add(segments.get(0).from());
    for (RouteSegment segment : segments) {
      stops.add(segment.to());
    }
    return List.copyOf(stops);
  }

  public int stopCount() {
    return segments.size() + 1;
  }

  public City origin() {
    return segments.get(0).from();
  }

  public City destination() {
    return segments.get(segments.size() - 1).to();
  }

  public int indexOf(String cityId) {
    if (origin().id().equals(cityId)) {
      return 0;
    }
    for (int i = 0; i < segments.size(); i++) {
      if (segments.get(i).to().id().equals(cityId)) {
        return i + 1;
      }
    }
    return -1;
  }

  public boolean servesDirection(String fromCityId, String toCityId) {
    int from = indexOf(fromCityId);
    int to = indexOf(toCityId);
    return from >= 0 && to > from;
  }

  public int[] stopRange(String fromCityId, String toCityId) {
    if (!servesDirection(fromCityId, toCityId)) {
      throw new IllegalArgumentException(
          "route " + number + " does not go " + fromCityId + " -> " + toCityId);
    }
    return new int[] {indexOf(fromCityId), indexOf(toCityId)};
  }

  public double totalDistanceKm() {
    return segments.stream().mapToDouble(RouteSegment::distanceKm).sum();
  }

  public double distanceKm(String fromCityId, String toCityId) {
    int[] range = stopRange(fromCityId, toCityId);
    double km = 0;
    for (int i = range[0]; i < range[1]; i++) {
      km += segments.get(i).distanceKm();
    }
    return km;
  }

  public Duration arrivalOffset(int stopIndex) {
    checkStopIndex(stopIndex);
    Duration offset = Duration.ZERO;
    for (int i = 0; i < stopIndex; i++) {
      offset = offset.plus(segments.get(i).travelTime());
      if (i < stopIndex - 1) {
        offset = offset.plus(segments.get(i).dwellAtEnd());
      }
    }
    return offset;
  }

  public Duration dwellAt(int stopIndex) {
    checkStopIndex(stopIndex);
    return stopIndex == 0 ? Duration.ZERO : segments.get(stopIndex - 1).dwellAtEnd();
  }

  public Duration departureOffset(int stopIndex) {
    return arrivalOffset(stopIndex).plus(dwellAt(stopIndex));
  }

  public Duration totalDuration() {
    return arrivalOffset(stopCount() - 1);
  }

  public Duration travelTime(String fromCityId, String toCityId) {
    int[] range = stopRange(fromCityId, toCityId);
    return arrivalOffset(range[1]).minus(departureOffset(range[0]));
  }

  private void checkStopIndex(int stopIndex) {
    if (stopIndex < 0 || stopIndex >= stopCount()) {
      throw new IndexOutOfBoundsException("stop index " + stopIndex + " of " + stopCount());
    }
  }
}
