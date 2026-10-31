package transport.repository.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import transport.model.Route;
import transport.model.Trip;
import transport.model.TripStatus;

/**
 * {@link Trip} у JSON посилається на маршрут за номером:
 *
 * <pre>{"id":"T1","routeNumber":12,"date":"2026-09-23","departureTime":"08:00:00","busId":"AA1234","status":"SCHEDULED"}</pre>
 *
 * Маршрут при читанні береться з каталогу ({@link JsonCodec#withRoutes}).
 */
final class TripJson {

  private TripJson() {}

  static final class Serializer extends StdSerializer<Trip> {
    private static final long serialVersionUID = 1L;

    Serializer() {
      super(Trip.class);
    }

    @Override
    public void serialize(Trip trip, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      gen.writeStartObject();
      gen.writeStringField("id", trip.getId());
      gen.writeNumberField("routeNumber", trip.getRoute().number());
      provider.defaultSerializeField("date", trip.getDate(), gen);
      provider.defaultSerializeField("departureTime", trip.getDepartureTime(), gen);
      if (trip.hasBus()) {
        gen.writeStringField("busId", trip.getBusId());
      }
      gen.writeStringField("status", trip.getStatus().name());
      gen.writeEndObject();
    }
  }

  static final class Deserializer extends StdDeserializer<Trip> {
    private static final long serialVersionUID = 1L;

    Deserializer() {
      super(Trip.class);
    }

    @Override
    public Trip deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
      JsonNode node = ctxt.readTree(parser);
      String id = required(ctxt, node, "id").asText();
      Route route = route(ctxt, required(ctxt, node, "routeNumber").asInt());
      LocalDate date = ctxt.readTreeAsValue(required(ctxt, node, "date"), LocalDate.class);
      LocalTime time = ctxt.readTreeAsValue(required(ctxt, node, "departureTime"), LocalTime.class);
      JsonNode bus = node.get("busId");
      TripStatus status = ctxt.readTreeAsValue(required(ctxt, node, "status"), TripStatus.class);
      try {
        return new Trip(id, route, date, time, bus == null || bus.isNull() ? null : bus.asText(),
            status);
      } catch (IllegalArgumentException e) {
        return ctxt.reportInputMismatch(this, "invalid trip %s: %s", id, e.getMessage());
      }
    }

    private static JsonNode required(DeserializationContext ctxt, JsonNode node, String field)
        throws IOException {
      JsonNode value = node.get(field);
      if (value == null || value.isNull()) {
        return ctxt.reportInputMismatch(Trip.class, "trip: missing '%s'", field);
      }
      return value;
    }

    private static Route route(DeserializationContext ctxt, int number) throws IOException {
      @SuppressWarnings("unchecked")
      Map<Integer, Route> routes = (Map<Integer, Route>) ctxt.getAttribute(JsonCodec.ROUTES_ATTRIBUTE);
      if (routes == null) {
        return ctxt.reportInputMismatch(
            Trip.class, "trips need a route catalogue: use JsonCodec.withRoutes(...)");
      }
      Route route = routes.get(number);
      if (route == null) {
        return ctxt.reportInputMismatch(Trip.class, "unknown route %d", number);
      }
      return route;
    }
  }
}
