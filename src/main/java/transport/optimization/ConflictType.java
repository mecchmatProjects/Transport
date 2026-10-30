package transport.optimization;

public enum ConflictType {
  OVERLAP,
  TURNAROUND,
  /** Наступний рейс в іншому місті, і на підгін не вистачає часу. */
  WRONG_LOCATION,
  WORKING_HOURS,
  BUS_UNAVAILABLE,
  CAPACITY
}
