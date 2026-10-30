package transport.optimization;

import java.util.Objects;

public record Conflict(ConflictType type, String busId, String description) {

  public Conflict {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(busId, "busId");
    Objects.requireNonNull(description, "description");
  }
}
