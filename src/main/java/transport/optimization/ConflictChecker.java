package transport.optimization;

import java.util.List;

/** Реалізація — задача M2. */
public final class ConflictChecker {

  public List<Conflict> check(Schedule schedule, ProblemInstance problem) {
    throw new UnsupportedOperationException("M2");
  }

  /** Розклад не змінюється. */
  public boolean canAssign(Schedule schedule, Assignment candidate, ProblemInstance problem) {
    throw new UnsupportedOperationException("M2");
  }
}
