package transport.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Шаблон розкладу: за яким маршрутом, о котрій і в які дні тижня має відбуватися рейс. Це джерело
 * початкового розкладу для {@code ScheduleService}: шаблон розгортається у конкретні {@link Trip}
 * на дату, після чого оптимізатор призначає автобуси і може зсунути відправлення в межах {@code
 * flex}.
 *
 * @param id ідентифікатор шаблону
 * @param routeNumber номер маршруту ({@link Route#number()})
 * @param departureTimes бажані часи відправлення з першої зупинки (без повторів, зберігаються
 *     відсортованими)
 * @param days дні тижня, коли шаблон діє
 * @param flex на скільки оптимізатор може зсунути відправлення в будь-який бік
 * @param priority пріоритет (більше значення важливіше); оптимізатор першими відкидає рейси з
 *     меншим пріоритетом
 * @param expectedPassengers очікувана кількість пасажирів на рейсі
 * @param expectedLuggageKg очікувана вага багажу на рейсі, кг
 */
public record ScheduleTemplate(
    String id,
    int routeNumber,
    List<LocalTime> departureTimes,
    Set<DayOfWeek> days,
    Duration flex,
    int priority,
    int expectedPassengers,
    double expectedLuggageKg) {

  private static final DateTimeFormatter DATE_ID = DateTimeFormatter.BASIC_ISO_DATE;
  private static final DateTimeFormatter TIME_ID = DateTimeFormatter.ofPattern("HHmm");

  public ScheduleTemplate {
    Checks.requireText(id, "id");
    Checks.requirePositive(routeNumber, "routeNumber");
    Objects.requireNonNull(departureTimes, "departureTimes");
    if (departureTimes.isEmpty()) {
      throw new IllegalArgumentException("template " + id + " has no departure times");
    }
    TreeSet<LocalTime> sorted = new TreeSet<>(departureTimes);
    if (sorted.size() != departureTimes.size()) {
      throw new IllegalArgumentException("template " + id + " has duplicate departure times");
    }
    departureTimes = List.copyOf(sorted);
    Objects.requireNonNull(days, "days");
    if (days.isEmpty()) {
      throw new IllegalArgumentException("template " + id + " has no days");
    }
    days = Collections.unmodifiableSet(EnumSet.copyOf(days));
    Checks.requireNonNegative(flex, "flex");
    if (priority < 0) {
      throw new IllegalArgumentException("priority must be >= 0, got " + priority);
    }
    if (expectedPassengers < 0) {
      throw new IllegalArgumentException(
          "expectedPassengers must be >= 0, got " + expectedPassengers);
    }
    Checks.requireNonNegative(expectedLuggageKg, "expectedLuggageKg");
  }

  /** Шаблон на всі дні тижня. */
  public static ScheduleTemplate daily(
      String id, int routeNumber, List<LocalTime> departureTimes, Duration flex,
      int expectedPassengers) {
    return new ScheduleTemplate(
        id, routeNumber, departureTimes, EnumSet.allOf(DayOfWeek.class), flex, 1,
        expectedPassengers, 0);
  }

  /** Чи діє шаблон у цю дату. */
  public boolean runsOn(LocalDate date) {
    return days.contains(date.getDayOfWeek());
  }

  /**
   * Розгортає шаблон у рейси на дату: по одному {@link Trip} без автобуса на кожен час
   * відправлення. Ідентифікатор рейсу детермінований: {@code <id>-<yyyyMMdd>-<HHmm>}, тож повторне
   * розгортання дає ті самі id.
   *
   * @return порожній список, якщо в цей день шаблон не діє
   * @throws IllegalArgumentException якщо {@code route} не той, на який посилається шаблон
   */
  public List<Trip> tripsOn(LocalDate date, Route route) {
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(route, "route");
    if (route.number() != routeNumber) {
      throw new IllegalArgumentException(
          "template " + id + " is for route " + routeNumber + ", got " + route.number());
    }
    if (!runsOn(date)) {
      return List.of();
    }
    List<Trip> trips = new ArrayList<>(departureTimes.size());
    for (LocalTime time : departureTimes) {
      trips.add(new Trip(tripId(date, time), route, date, time));
    }
    return List.copyOf(trips);
  }

  /** Ідентифікатор рейсу, який шаблон створює для цієї дати й часу. */
  public String tripId(LocalDate date, LocalTime time) {
    return id + "-" + date.format(DATE_ID) + "-" + time.format(TIME_ID);
  }

  /** Чи вкладається фактичне відправлення в допустимий зсув від бажаного. */
  public boolean withinFlex(LocalDateTime planned, LocalDateTime actual) {
    return Duration.between(planned, actual).abs().compareTo(flex) <= 0;
  }
}
