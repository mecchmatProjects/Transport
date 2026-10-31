package transport.repository.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import transport.model.BusStatus;
import transport.model.Contact;
import transport.model.LuggageStatus;
import transport.model.Passenger;
import transport.model.PassengerLuggage;
import transport.model.Shipment;
import transport.model.TicketStatus;
import transport.model.TimeWindow;

/**
 * Jackson-анотації для класів моделі, винесені в mix-in, щоб пакет {@code model} не залежав від
 * Jackson. Record-и ({@code City}, {@code Route}, {@code ScheduleTemplate} …) Jackson читає сам,
 * mix-in потрібні лише звичайним класам з кількома конструкторами та ієрархії багажу.
 */
final class ModelMixins {

  private ModelMixins() {}

  /** Поліморфний багаж: у JSON поле {@code "kind"} визначає підклас. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = PassengerLuggage.class, name = "PASSENGER"),
    @JsonSubTypes.Type(value = Shipment.class, name = "SHIPMENT")
  })
  abstract static class LuggageMixin {}

  abstract static class PassengerLuggageMixin {
    @JsonCreator
    PassengerLuggageMixin(
        @JsonProperty("id") String id,
        @JsonProperty("tripId") String tripId,
        @JsonProperty("fromCityId") String fromCityId,
        @JsonProperty("toCityId") String toCityId,
        @JsonProperty("weightKg") double weightKg,
        @JsonProperty("volumeM3") double volumeM3,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("status") LuggageStatus status,
        @JsonProperty("ticketNumber") String ticketNumber) {}
  }

  abstract static class ShipmentMixin {
    @JsonCreator
    ShipmentMixin(
        @JsonProperty("id") String id,
        @JsonProperty("tripId") String tripId,
        @JsonProperty("fromCityId") String fromCityId,
        @JsonProperty("toCityId") String toCityId,
        @JsonProperty("weightKg") double weightKg,
        @JsonProperty("volumeM3") double volumeM3,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("status") LuggageStatus status,
        @JsonProperty("sender") Contact sender,
        @JsonProperty("recipient") Contact recipient) {}
  }

  abstract static class BusMixin {
    @JsonCreator
    BusMixin(
        @JsonProperty("id") String id,
        @JsonProperty("model") String model,
        @JsonProperty("seats") int seats,
        @JsonProperty("luggageCapacityKg") double luggageCapacityKg,
        @JsonProperty("depotCityId") String depotCityId,
        @JsonProperty("status") BusStatus status,
        @JsonProperty("unavailablePeriods") Collection<TimeWindow> unavailablePeriods) {}
  }

  abstract static class TicketMixin {
    @JsonCreator
    TicketMixin(
        @JsonProperty("number") String number,
        @JsonProperty("tripId") String tripId,
        @JsonProperty("passenger") Passenger passenger,
        @JsonProperty("fromCityId") String fromCityId,
        @JsonProperty("toCityId") String toCityId,
        @JsonProperty("seatNumber") int seatNumber,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("soldAt") LocalDateTime soldAt,
        @JsonProperty("status") TicketStatus status) {}

    @JsonIgnore
    abstract boolean isActive(); // похідне від status, у JSON не пишемо
  }

  abstract static class TariffMixin {
    @JsonIgnore
    abstract boolean isRouteSpecific();
  }
}
