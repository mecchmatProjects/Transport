package transport.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CapacityExceptionsTest {

  @Test
  void noSeatsCarriesTripAndDirection() {
    CapacityExceededException e = new NoSeatsAvailableException("T1", "KYIV", "RIVNE");
    assertThat(e).isInstanceOf(TransportException.class);
    assertThat(e.getTripId()).isEqualTo("T1");
    assertThat(((NoSeatsAvailableException) e).getToCityId()).isEqualTo("RIVNE");
    assertThat(e.getMessage()).contains("KYIV -> RIVNE");
  }

  @Test
  void luggageCarriesSegmentAndWeights() {
    LuggageCapacityExceededException e = new LuggageCapacityExceededException("T1", 1, 50, 20);
    assertThat(e).isInstanceOf(CapacityExceededException.class);
    assertThat(e.getSegmentIndex()).isEqualTo(1);
    assertThat(e.getRequestedKg()).isEqualTo(50.0);
    assertThat(e.getAvailableKg()).isEqualTo(20.0);
  }
}
