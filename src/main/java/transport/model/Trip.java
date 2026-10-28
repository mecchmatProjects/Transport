package transport.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Рейс — конкретний виїзд за маршрутом у певну дату й час (README §3).
 *
 * <p>Автобус посилається за id і може бути ще не призначений ({@code null}) — його призначає
 * оптимізатор або диспетчер. Квитки й багаж посилаються на рейс за {@link #getId()} і
 * зберігаються у своїх репозиторіях, щоб не було циклічних посилань.
 */
public class Trip {

  private final String id;
  private final Route route;
  private final LocalDate date;
  private LocalTime departureTime;
  private String busId;
  private TripStatus status;

  /** Новий рейс у статусі {@link TripStatus#SCHEDULED} без автобуса. */
  public Trip(String id, Route route, LocalDate date, LocalTime departureTime) {
    this(id, route, date, departureTime, null, TripStatus.SCHEDULED);
  }

  /** Повний конструктор (для завантаження зі сховища). */
  public Trip(
      String id,
      Route route,
      LocalDate date,
      LocalTime departureTime,
      String busId,
      TripStatus status) {
    this.id = Checks.requireText(id, "id");
    this.route = Objects.requireNonNull(route, "route");
    this.date = Objects.requireNonNull(date, "date");
    this.departureTime = Objects.requireNonNull(departureTime, "departureTime");
    this.busId = busId;
    this.status = Objects.requireNonNull(status, "status");
  }

  public String getId() {
    return id;
  }

  public Route getRoute() {
    return route;
  }

  public LocalDate getDate() {
    return date;
  }

  public LocalTime getDepartureTime() {
    return departureTime;
  }

  /** Id призначеного автобуса або {@code null}. */
  public String getBusId() {
    return busId;
  }

  public boolean hasBus() {
    return busId != null;
  }

  public TripStatus getStatus() {
    return status;
  }

  /** Призначає автобус. Дозволено лише до відправлення. */
  public void assignBus(String busId) {
    requireScheduled("assign bus");
    this.busId = Checks.requireText(busId, "busId");
  }

  public void unassignBus() {
    requireScheduled("unassign bus");
    this.busId = null;
  }

  /** Змінює час відправлення (крок локального пошуку). Дозволено лише до відправлення. */
  public void reschedule(LocalTime newDepartureTime) {
    requireScheduled("reschedule");
    this.departureTime = Objects.requireNonNull(newDepartureTime, "newDepartureTime");
  }

  /** Переводить рейс у новий статус за правилами {@link TripStatus}. */
  public void changeStatus(TripStatus next) {
    this.status = Transitional.transition(status, next);
  }

  public LocalDateTime departure() {
    return LocalDateTime.of(date, departureTime);
  }

  public LocalDateTime arrival() {
    return departure().plus(route.totalDuration());
  }

  /** Час, коли автобус зайнятий цим рейсом. */
  public TimeWindow timeWindow() {
    return new TimeWindow(departure(), arrival());
  }

  /** Відправлення з міста {@code cityId}. */
  public LocalDateTime departureFrom(String cityId) {
    return departure().plus(route.departureOffset(requireStop(cityId)));
  }

  /** Прибуття в місто {@code cityId}. */
  public LocalDateTime arrivalAt(String cityId) {
    return departure().plus(route.arrivalOffset(requireStop(cityId)));
  }

  /** Розклад зупинок цього рейсу. */
  public List<ScheduledStop> scheduledStops() {
    List<City> stops = route.stops();
    List<ScheduledStop> result = new ArrayList<>(stops.size());
    LocalDateTime start = departure();
    for (int i = 0; i < stops.size(); i++) {
      result.add(
          new ScheduledStop(
              i,
              stops.get(i),
              start.plus(route.arrivalOffset(i)),
              start.plus(route.departureOffset(i))));
    }
    return List.copyOf(result);
  }

  /**
   * Чи не може один автобус виконати і цей, і інший рейс (README §9). Між рейсами потрібен час на
   * розворот {@code turnaround}, а наступний рейс має починатись у місті, де закінчився попередній,
   * — це вже перевіряє оптимізатор/{@code ScheduleService}.
   */
  public boolean conflictsWith(Trip other, Duration turnaround) {
    if (this.equals(other)) {
      return false;
    }
    return timeWindow().extendedBy(turnaround).overlaps(other.timeWindow().extendedBy(turnaround));
  }

  private int requireStop(String cityId) {
    int index = route.indexOf(cityId);
    if (index < 0) {
      throw new IllegalArgumentException("trip " + id + " does not stop at " + cityId);
    }
    return index;
  }

  private void requireScheduled(String action) {
    if (status != TripStatus.SCHEDULED) {
      throw new IllegalStateException("cannot " + action + " for trip " + id + " in status " + status);
    }
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof Trip other && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Trip[" + id + ", route " + route.number() + ", " + departure() + ", bus=" + busId
        + ", " + status + "]";
  }
}
