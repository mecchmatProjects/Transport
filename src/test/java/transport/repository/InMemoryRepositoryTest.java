package transport.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import transport.model.City;

class InMemoryRepositoryTest {

  private final City kyiv = new City("KYIV", "Kyiv", 50.45, 30.52);
  private final City lviv = new City("LVIV", "Lviv", 49.84, 24.03);
  private final City odesa = new City("ODESA", "Odesa", 46.48, 30.72);

  private final Repository<City, String> repo = new InMemoryRepository<>("City", City::id);

  @Test
  void keepsInsertionOrderWhenReplacing() {
    repo.add(kyiv);
    repo.add(lviv);
    repo.add(odesa);
    City renamed = new City("LVIV", "Lviv (renamed)", 49.84, 24.03);
    repo.save(renamed);

    assertThat(repo.findAll()).containsExactly(kyiv, renamed, odesa);
    assertThat(repo.count()).isEqualTo(3L);
  }

  @Test
  void findAllIsASnapshot() {
    repo.add(kyiv);
    var snapshot = repo.findAll();
    repo.add(lviv);

    assertThat(snapshot).containsExactly(kyiv);
    assertThatThrownBy(() -> snapshot.add(odesa))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void deleteReportsWhetherEntityExisted() {
    repo.add(kyiv);
    assertThat(repo.deleteById("KYIV")).isTrue();
    assertThat(repo.deleteById("KYIV")).isFalse();
    assertThat(repo.findById("KYIV")).isEmpty();
  }

  @Test
  void rejectsNullEntityAndNullId() {
    Repository<City, String> broken = new InMemoryRepository<>("City", c -> null);
    assertThatThrownBy(() -> repo.save(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> broken.save(kyiv))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("City id");
  }
}
