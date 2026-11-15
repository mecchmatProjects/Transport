package transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import transport.exception.ValidationException;
import transport.model.City;
import transport.model.Route;
import transport.model.RouteSegment;
import transport.model.ScheduleTemplate;
import transport.model.Tariff;
import transport.repository.TariffKey;

class RouteServiceTest {

  private final ServiceFixture f = new ServiceFixture();
  private final RouteService service = f.routeService;

  @Test
  void rejects31stRoute() {
    for (int number = 1; f.routes.count() < RouteService.DEFAULT_MAX_ROUTES; number++) {
      if (number != 12) {
        service.create(ServiceFixture.kyivLutsk(number));
      }
    }

    assertThat(service.count()).isEqualTo(30L);
    assertThatThrownBy(() -> service.create(ServiceFixture.kyivLutsk(99)))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("30");
  }

  @Test
  void limitIsConfigurable() {
    RouteService small =
        new RouteService(f.routes, f.cities, f.trips, f.templates, f.tariffs, 1);

    assertThatThrownBy(() -> small.create(ServiceFixture.kyivLutsk(13)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsUnknownCity() {
    City lviv = new City("LVIV", "Lviv", 49.84, 24.03);
    Route route = new Route(20, "Kyiv - Lviv",
        List.of(new RouteSegment(ServiceFixture.KYIV, lviv, 540, Duration.ofHours(7))));

    assertThatThrownBy(() -> service.create(route))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("LVIV");
  }

  @Test
  void findsRoutesByDirection() {
    assertThat(service.findRoutesBetween("ZHYTOMYR", "LUTSK")).containsExactly(f.route12);
    assertThat(service.findRoutesBetween("LUTSK", "KYIV")).isEmpty();
  }

  @Test
  void cannotChangeOrDeleteRouteWithTrips() {
    f.trips.add(f.trip("T1", LocalTime.of(8, 0)));
    Route renamed = new Route(12, "Renamed", f.route12.segments());

    assertThatThrownBy(() -> service.update(renamed)).hasMessageContaining("has trips");
    assertThatThrownBy(() -> service.delete(12)).hasMessageContaining("has trips");
  }

  @Test
  void updateMustKeepOwnTariffsValid() {
    f.tariffs.add(new Tariff("RIVNE", "LUTSK", 12, new BigDecimal("180"), BigDecimal.ONE));
    Route shortened = Route.withUniformDwell(12, "Kyiv - Rivne",
        List.of(new RouteSegment(ServiceFixture.KYIV, ServiceFixture.ZHYTOMYR, 140,
                Duration.ofMinutes(120)),
            new RouteSegment(ServiceFixture.ZHYTOMYR, ServiceFixture.RIVNE, 190,
                Duration.ofMinutes(150))),
        Duration.ofMinutes(10));

    assertThatThrownBy(() -> service.update(shortened))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("RIVNE -> LUTSK");

    Route renamed = new Route(12, "Renamed", f.route12.segments());
    service.update(renamed);
    assertThat(service.get(12).name()).isEqualTo("Renamed");
  }

  @Test
  void cannotDeleteRouteWithTemplateOrOwnTariff() {
    f.templates.add(ScheduleTemplate.daily(
        "R12-AM", 12, List.of(LocalTime.of(8, 0)), Duration.ofMinutes(30), 40));
    assertThatThrownBy(() -> service.delete(12)).hasMessageContaining("R12-AM");

    f.templates.deleteById("R12-AM");
    f.tariffs.add(new Tariff("KYIV", "LUTSK", 12, new BigDecimal("550"), BigDecimal.ONE));
    assertThatThrownBy(() -> service.delete(12)).hasMessageContaining("tariffs");

    f.tariffs.findAll().forEach(t -> f.tariffs.deleteById(TariffKey.of(t)));
    service.delete(12);
    assertThat(service.list()).isEmpty();
  }
}
