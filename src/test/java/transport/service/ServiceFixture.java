package transport.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import transport.model.Bus;
import transport.model.City;
import transport.model.Luggage;
import transport.model.Passenger;
import transport.model.Route;
import transport.model.RouteSegment;
import transport.model.ScheduleTemplate;
import transport.model.Tariff;
import transport.model.Ticket;
import transport.model.Trip;
import transport.repository.InMemoryRepository;
import transport.repository.Repository;
import transport.repository.TariffKey;

/** Каталог у пам'яті: Kyiv → Zhytomyr → Rivne → Lutsk (маршрут 12, 6:00) і автобус AA1234. */
final class ServiceFixture {

  static final City KYIV = new City("KYIV", "Kyiv", 50.4501, 30.5234);
  static final City ZHYTOMYR = new City("ZHYTOMYR", "Zhytomyr", 50.2547, 28.6587);
  static final City RIVNE = new City("RIVNE", "Rivne", 50.6199, 26.2516);
  static final City LUTSK = new City("LUTSK", "Lutsk", 50.7472, 25.3254);
  static final City UMAN = new City("UMAN", "Uman", 48.7484, 30.2219);
  static final LocalDate DATE = LocalDate.of(2026, 9, 23);

  final Repository<City, String> cities = new InMemoryRepository<>("City", City::id);
  final Repository<Bus, String> buses = new InMemoryRepository<>("Bus", Bus::getId);
  final Repository<Route, Integer> routes = new InMemoryRepository<>("Route", Route::number);
  final Repository<Tariff, TariffKey> tariffs = new InMemoryRepository<>("Tariff", TariffKey::of);
  final Repository<ScheduleTemplate, String> templates =
      new InMemoryRepository<>("ScheduleTemplate", ScheduleTemplate::id);
  final Repository<Trip, String> trips = new InMemoryRepository<>("Trip", Trip::getId);
  final Repository<Ticket, String> tickets = new InMemoryRepository<>("Ticket", Ticket::getNumber);
  final Repository<Luggage, String> luggage = new InMemoryRepository<>("Luggage", Luggage::getId);

  final CityService cityService = new CityService(cities, routes, buses, tariffs);
  final BusService busService = new BusService(buses, cities, trips);
  final RouteService routeService = new RouteService(routes, cities, trips, templates, tariffs);
  final TariffService tariffService = new TariffService(tariffs, cities, routes);
  final TripService tripService = new TripService(trips, routes, buses, tickets, luggage);

  final Route route12 = kyivLutsk(12);

  ServiceFixture() {
    List.of(KYIV, ZHYTOMYR, RIVNE, LUTSK, UMAN).forEach(cities::add);
    routes.add(route12);
    buses.add(new Bus("AA1234", "Setra", 52, 1500, "KYIV"));
  }

  static Route kyivLutsk(int number) {
    return Route.withUniformDwell(
        number,
        "Kyiv - Lutsk",
        List.of(
            new RouteSegment(KYIV, ZHYTOMYR, 140, Duration.ofMinutes(120)),
            new RouteSegment(ZHYTOMYR, RIVNE, 190, Duration.ofMinutes(150)),
            new RouteSegment(RIVNE, LUTSK, 75, Duration.ofMinutes(70))),
        Duration.ofMinutes(10));
  }

  Trip trip(String id, LocalTime departure) {
    return new Trip(id, route12, DATE, departure);
  }

  Ticket ticket(String number, String tripId) {
    return new Ticket(number, tripId, new Passenger("P1", "Ivan Petrenko", null), "KYIV", "RIVNE",
        1, new BigDecimal("450"), LocalDateTime.of(2026, 9, 1, 12, 0));
  }
}
