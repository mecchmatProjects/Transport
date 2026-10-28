package transport.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Спільні фікстури для тестів моделі: маршрут Kyiv → Zhytomyr → Rivne → Lutsk з README. */
final class TestData {

  static final City KYIV = new City("KYIV", "Kyiv", 50.4501, 30.5234);
  static final City ZHYTOMYR = new City("ZHYTOMYR", "Zhytomyr", 50.2547, 28.6587);
  static final City RIVNE = new City("RIVNE", "Rivne", 50.6199, 26.2516);
  static final City LUTSK = new City("LUTSK", "Lutsk", 50.7472, 25.3254);

  /**
   * Їзда 2:00 + 2:30 + 1:10, стоянки по 10 хв на двох проміжних зупинках → тривалість 6:00.
   * Відстані 140 + 190 + 75 = 405 км.
   */
  static Route kyivLutsk() {
    return Route.withUniformDwell(
        12,
        "Kyiv - Lutsk",
        List.of(
            new RouteSegment(KYIV, ZHYTOMYR, 140, Duration.ofMinutes(120)),
            new RouteSegment(ZHYTOMYR, RIVNE, 190, Duration.ofMinutes(150)),
            new RouteSegment(RIVNE, LUTSK, 75, Duration.ofMinutes(70))),
        Duration.ofMinutes(10));
  }

  static final LocalDate DATE = LocalDate.of(2026, 9, 23);

  static Trip trip(String id, LocalTime departure) {
    return new Trip(id, kyivLutsk(), DATE, departure);
  }

  private TestData() {}
}
