package transport.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Маршрут — незмінна послідовність зупинок (README §3), напр. {@code Kyiv → Zhytomyr → Rivne →
 * Lutsk}. Конкретний виїзд за маршрутом — це {@link Trip}.
 *
 * <p>Зупинки нумеруються з 0. Ділянка {@code i} з'єднує зупинки {@code i} та {@code i + 1}. Квиток
 * {@code from → to} займає ділянки {@code [index(from); index(to))}.
 *
 * @param number номер маршруту (> 0)
 * @param name назва
 * @param segments ділянки в порядку руху, кожна наступна починається там, де закінчилась попередня
 * @param dwellTime тривалість стоянки на кожній проміжній зупинці
 */
public record Route(int number, String name, List<RouteSegment> segments, Duration dwellTime) {

  public Route {
    Checks.requirePositive(number, "number");
    Checks.requireText(name, "name");
    Objects.requireNonNull(segments, "segments");
    if (segments.isEmpty()) {
      throw new IllegalArgumentException("route " + number + " must have at least one segment");
    }
    segments = List.copyOf(segments);
    Checks.requireNonNegative(dwellTime, "dwellTime");

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

  /** Маршрут без стоянок на проміжних зупинках. */
  public Route(int number, String name, List<RouteSegment> segments) {
    this(number, name, segments, Duration.ZERO);
  }

  /** Усі зупинки по порядку. */
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

  /** Індекс зупинки або {@code -1}, якщо міста на маршруті немає. */
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

  /** Чи можна проїхати цим маршрутом з {@code fromCityId} до {@code toCityId} (у напрямку руху). */
  public boolean servesDirection(String fromCityId, String toCityId) {
    int from = indexOf(fromCityId);
    int to = indexOf(toCityId);
    return from >= 0 && to > from;
  }

  /**
   * Пара індексів зупинок {@code [from, to]} для поїздки.
   *
   * @throws IllegalArgumentException якщо маршрут не обслуговує цей напрямок
   */
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

  /** Відстань між двома зупинками маршруту, км. */
  public double distanceKm(String fromCityId, String toCityId) {
    int[] range = stopRange(fromCityId, toCityId);
    double km = 0;
    for (int i = range[0]; i < range[1]; i++) {
      km += segments.get(i).distanceKm();
    }
    return km;
  }

  /** Зсув часу прибуття на зупинку {@code stopIndex} відносно відправлення з першої. */
  public Duration arrivalOffset(int stopIndex) {
    checkStopIndex(stopIndex);
    Duration offset = Duration.ZERO;
    for (int i = 0; i < stopIndex; i++) {
      offset = offset.plus(segments.get(i).travelTime());
    }
    int intermediateStopsPassed = Math.max(0, stopIndex - 1);
    return offset.plus(dwellTime.multipliedBy(intermediateStopsPassed));
  }

  /** Зсув часу відправлення із зупинки {@code stopIndex} (для останньої дорівнює прибуттю). */
  public Duration departureOffset(int stopIndex) {
    Duration arrival = arrivalOffset(stopIndex);
    boolean intermediate = stopIndex > 0 && stopIndex < stopCount() - 1;
    return intermediate ? arrival.plus(dwellTime) : arrival;
  }

  /** Орієнтовна тривалість рейсу від першої до останньої зупинки. */
  public Duration totalDuration() {
    return arrivalOffset(stopCount() - 1);
  }

  /** Час у дорозі між двома зупинками маршруту, разом зі стоянками між ними. */
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
