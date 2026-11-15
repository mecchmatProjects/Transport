package transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static transport.service.ServiceFixture.KYIV;
import static transport.service.ServiceFixture.UMAN;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import transport.exception.DuplicateEntityException;
import transport.exception.EntityNotFoundException;
import transport.exception.ValidationException;
import transport.model.Bus;
import transport.model.City;
import transport.model.Tariff;

class CityServiceTest {

  private final ServiceFixture f = new ServiceFixture();
  private final CityService service = f.cityService;

  @Test
  void createRejectsDuplicateId() {
    assertThatThrownBy(() -> service.create(new City("KYIV", "Kyiv 2", 50, 30)))
        .isInstanceOf(DuplicateEntityException.class);
  }

  @Test
  void getUpdateDeleteRejectUnknownId() {
    assertThatThrownBy(() -> service.get("NOWHERE")).isInstanceOf(EntityNotFoundException.class);
    assertThatThrownBy(() -> service.update(new City("NOWHERE", "X", 1, 1)))
        .isInstanceOf(EntityNotFoundException.class);
    assertThatThrownBy(() -> service.delete("NOWHERE"))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void updateReplacesCity() {
    City renamed = new City("UMAN", "Uman (renamed)", UMAN.latitude(), UMAN.longitude());
    service.update(renamed);

    assertThat(service.get("UMAN").name()).isEqualTo("Uman (renamed)");
  }

  @Test
  void deletesUnusedCity() {
    service.delete("UMAN");

    assertThat(service.list()).doesNotContain(UMAN);
  }

  @Test
  void cannotDeleteCityOnRoute() {
    assertThatThrownBy(() -> service.delete("RIVNE"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("route 12");
  }

  @Test
  void cannotDeleteDepotOrTariffCity() {
    f.buses.add(new Bus("UM0001", "Bogdan", 30, 500, "UMAN"));
    assertThatThrownBy(() -> service.delete("UMAN")).hasMessageContaining("depot");

    f.buses.deleteById("UM0001");
    f.tariffs.add(Tariff.general("UMAN", KYIV.id(), new BigDecimal("300"), BigDecimal.ONE));
    assertThatThrownBy(() -> service.delete("UMAN")).hasMessageContaining("tariffs");
  }
}
