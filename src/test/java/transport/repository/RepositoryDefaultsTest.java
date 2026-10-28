package transport.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import transport.exception.DuplicateEntityException;
import transport.exception.EntityNotFoundException;
import transport.model.City;

/** Перевіряє default-методи {@link Repository} на мінімальній реалізації. */
class RepositoryDefaultsTest {

  private static final class MapRepository implements Repository<City, String> {
    private final Map<String, City> data = new LinkedHashMap<>();

    @Override
    public String idOf(City city) {
      return city.id();
    }

    @Override
    public String entityName() {
      return "City";
    }

    @Override
    public Optional<City> findById(String id) {
      return Optional.ofNullable(data.get(id));
    }

    @Override
    public List<City> findAll() {
      return new ArrayList<>(data.values());
    }

    @Override
    public City save(City city) {
      data.put(city.id(), city);
      return city;
    }

    @Override
    public boolean deleteById(String id) {
      return data.remove(id) != null;
    }

    @Override
    public long count() {
      return data.size();
    }
  }

  private final City kyiv = new City("KYIV", "Kyiv", 50.45, 30.52);
  private final City lviv = new City("LVIV", "Lviv", 49.84, 24.03);

  @Test
  void addRejectsDuplicates() {
    Repository<City, String> repo = new MapRepository();
    repo.add(kyiv);
    assertThatThrownBy(() -> repo.add(kyiv))
        .isInstanceOf(DuplicateEntityException.class)
        .hasMessageContaining("City already exists: KYIV");
    repo.save(new City("KYIV", "Kyiv (renamed)", 50.45, 30.52));
    assertThat(repo.getById("KYIV").name()).isEqualTo("Kyiv (renamed)");
  }

  @Test
  void getByIdThrowsWhenMissing() {
    Repository<City, String> repo = new MapRepository();
    assertThatThrownBy(() -> repo.getById("ODESA"))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("City not found: ODESA");
    assertThat(repo.existsById("ODESA")).isFalse();
  }

  @Test
  void filteredFindAll() {
    Repository<City, String> repo = new MapRepository();
    repo.add(kyiv);
    repo.add(lviv);
    assertThat(repo.findAll(c -> c.longitude() < 25)).containsExactly(lviv);
    assertThat(repo.deleteById("KYIV")).isTrue();
    assertThat(repo.count()).isEqualTo(1L);
  }
}
