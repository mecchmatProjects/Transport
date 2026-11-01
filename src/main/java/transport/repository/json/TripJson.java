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
import java.util.Set;
import transport.model.Route;
import transport.model.Trip;
import transport.model.TripStatus;

/** У JSON маршрут записується за номером і при читанні береться з каталогу маршрутів. */
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
    private static final Set<String> FIELDS =
        Set.of("id", "routeNumber", "date", "departureTime", "busId", "status");

    Deserializer() {
      super(Trip.class);
    }

    @Override
    public Trip deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
      JsonNode node = ctxt.readTree(parser);
      JsonFields.checkKnown(ctxt, node, Trip.class, FIELDS);
      String id = JsonFields.requiredText(ctxt, node, Trip.class, "id");
      Route route = route(ctxt, JsonFields.requiredInt(ctxt, node, Trip.class, "routeNumber"));
      LocalDate date = ctxt.readTreeAsValue(
          JsonFields.required(ctxt, node, Trip.class, "date"), LocalDate.class);
      LocalTime time = ctxt.readTreeAsValue(
          JsonFields.required(ctxt, node, Trip.class, "departureTime"), LocalTime.class);
      TripStatus status = ctxt.readTreeAsValue(
          JsonFields.required(ctxt, node, Trip.class, "status"), TripStatus.class);
      String busId = node.hasNonNull("busId")
          ? JsonFields.requiredText(ctxt, node, Trip.class, "busId")
          : null;
      try {
        return new Trip(id, route, date, time, busId, status);
      } catch (IllegalArgumentException e) {
        return ctxt.reportInputMismatch(this, "invalid trip %s: %s", id, e.getMessage());
      }
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
