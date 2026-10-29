package transport.optimization;

import java.time.LocalDateTime;
import java.util.Objects;

public record Assignment(String demandId, String busId, LocalDateTime departure) {

  public Assignment {
    Objects.requireNonNull(demandId, "demandId");
    Objects.requireNonNull(busId, "busId");
    Objects.requireNonNull(departure, "departure");
  }
}
