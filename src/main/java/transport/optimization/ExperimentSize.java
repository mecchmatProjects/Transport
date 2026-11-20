package transport.optimization;

import java.util.List;

/** Розміри задач для експериментів, README §18. */
public record ExperimentSize(String name, int cities, int routes, int buses, int trips) {

  public static final ExperimentSize S1 = new ExperimentSize("S1", 10, 5, 3, 10);
  public static final ExperimentSize S2 = new ExperimentSize("S2", 20, 10, 5, 30);
  public static final ExperimentSize S3 = new ExperimentSize("S3", 30, 15, 8, 50);
  public static final ExperimentSize S4 = new ExperimentSize("S4", 50, 30, 15, 100);
  public static final List<ExperimentSize> ALL = List.of(S1, S2, S3, S4);

  public ExperimentSize {
    if (cities < 2 || routes < 1 || buses < 1 || trips < 0) {
      throw new IllegalArgumentException("invalid size " + name);
    }
  }
}
