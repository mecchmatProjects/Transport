package transport.model;

/** Статус квитка (README §4): {@code SOLD -> CANCELLED | USED}. */
public enum TicketStatus implements Transitional<TicketStatus> {
  SOLD,
  CANCELLED,
  USED;

  @Override
  public boolean canTransitionTo(TicketStatus next) {
    return this == SOLD && (next == CANCELLED || next == USED);
  }

  public boolean occupiesSeat() {
    return this == SOLD;
  }
}
