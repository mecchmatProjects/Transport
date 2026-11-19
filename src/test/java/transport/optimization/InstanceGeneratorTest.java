package transport.optimization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import transport.model.Bus;

class InstanceGeneratorTest {

  static List<ExperimentSize> sizes() {
    return ExperimentSize.ALL;
  }

  private static List<String> cityIds(ExperimentSize size) {
    return IntStream.rangeClosed(1, size.cities())
        .mapToObj(i -> String.format("C%02d", i))
        .toList();
  }

  private static List<String> describe(List<Bus> buses) {
    return buses.stream()
        .map(b -> b.getId() + " " + b.getSeats() + " " + b.getLuggageCapacityKg() + " "
            + b.getDepotCityId())
        .toList();
  }

  @ParameterizedTest
  @MethodSource("sizes")
  void sizeMatchesReadme(ExperimentSize size) {
    ProblemInstance problem = new InstanceGenerator(1).generate(size);

    assertThat(problem.buses()).hasSize(size.buses());
    assertThat(problem.demands()).hasSize(size.trips());
    assertThat(cityIds(size)).allMatch(problem.graph()::contains);
    assertThat(problem.demands().stream().map(d -> d.route().number()).distinct())
        .allMatch(n -> n >= 1 && n <= size.routes());
  }

  @ParameterizedTest
  @MethodSource("sizes")
  void sameSeedGivesSameInstance(ExperimentSize size) {
    ProblemInstance first = new InstanceGenerator(42).generate(size);
    ProblemInstance second = new InstanceGenerator(42).generate(size);

    assertThat(second.demands()).isEqualTo(first.demands());
    assertThat(describe(second.buses())).isEqualTo(describe(first.buses()));
  }

  @ParameterizedTest
  @MethodSource("sizes")
  void differentSeedGivesDifferentInstance(ExperimentSize size) {
    ProblemInstance first = new InstanceGenerator(1).generate(size);
    ProblemInstance second = new InstanceGenerator(2).generate(size);

    assertThat(second.demands()).isNotEqualTo(first.demands());
  }

  @ParameterizedTest
  @MethodSource("sizes")
  void allCitiesAreConnected(ExperimentSize size) {
    for (long seed = 1; seed <= 20; seed++) {
      CityGraph graph = new InstanceGenerator(seed).generate(size).graph();
      assertThat(cityIds(size))
          .allMatch(id -> Double.isFinite(graph.shortestDistanceKm("C01", id)));
    }
  }

  @ParameterizedTest
  @MethodSource("sizes")
  void everyTripFitsSomeBusAndDepotsAreInNetwork(ExperimentSize size) {
    for (long seed = 1; seed <= 20; seed++) {
      ProblemInstance problem = new InstanceGenerator(seed).generate(size);
      List<Bus> buses = problem.buses();

      assertThat(buses).allMatch(b -> problem.graph().contains(b.getDepotCityId()));
      assertThat(problem.demands())
          .allMatch(
              d ->
                  buses.stream()
                      .anyMatch(
                          b ->
                              d.expectedPassengers() <= b.getSeats()
                                  && d.expectedLuggageKg() <= b.getLuggageCapacityKg()));
    }
  }

  @Test
  void tooManyCitiesFailsInsteadOfHanging() {
    ExperimentSize tooBig = new ExperimentSize("X", 150, 10, 5, 10);
    assertThatThrownBy(() -> new InstanceGenerator(1).generate(tooBig))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @MethodSource("sizes")
  void tripsStayWithinTheDay(ExperimentSize size) {
    ProblemInstance problem = new InstanceGenerator(7).generate(size);

    assertThat(problem.demands())
        .allMatch(
            d ->
                d.earliestDeparture().toLocalDate().equals(InstanceGenerator.DAY)
                    && d.preferredDeparture().getHour() >= 5
                    && !d.preferredDeparture().isAfter(InstanceGenerator.DAY.atTime(20, 0)));
  }
}
