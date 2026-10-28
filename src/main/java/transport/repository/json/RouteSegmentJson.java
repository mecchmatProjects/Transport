package transport.repository.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import transport.model.City;
import transport.model.RouteSegment;

/**
 * {@link RouteSegment} у JSON зберігає не повні {@link City}, а лише їхні id:
 *
 * <pre>{"from":"KYIV","to":"ZHYTOMYR","distanceKm":140.0,"travelTime":"PT2H","dwellAtEnd":"PT10M"}</pre>
 *
 * Під час читання id замінюються на об'єкти з каталогу міст (атрибут {@link
 * JsonCodec#CITIES_ATTRIBUTE}). Так місто описане один раз у {@code cities.json}, і зміна його
 * координат не вимагає правити всі маршрути.
 */
final class RouteSegmentJson {

  private RouteSegmentJson() {}

  static final class Serializer extends StdSerializer<RouteSegment> {
    private static final long serialVersionUID = 1L;

    Serializer() {
      super(RouteSegment.class);
    }

    @Override
    public void serialize(RouteSegment segment, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      gen.writeStartObject();
      gen.writeStringField("from", segment.from().id());
      gen.writeStringField("to", segment.to().id());
      gen.writeNumberField("distanceKm", segment.distanceKm());
      provider.defaultSerializeField("travelTime", segment.travelTime(), gen);
      if (!segment.dwellAtEnd().isZero()) {
        provider.defaultSerializeField("dwellAtEnd", segment.dwellAtEnd(), gen);
      }
      gen.writeEndObject();
    }
  }

  static final class Deserializer extends StdDeserializer<RouteSegment> {
    private static final long serialVersionUID = 1L;

    Deserializer() {
      super(RouteSegment.class);
    }

    @Override
    public RouteSegment deserialize(JsonParser parser, DeserializationContext ctxt)
        throws IOException {
      JsonNode node = ctxt.readTree(parser);
      City from = city(ctxt, required(ctxt, node, "from").asText());
      City to = city(ctxt, required(ctxt, node, "to").asText());
      double km = required(ctxt, node, "distanceKm").asDouble();
      Duration travel = ctxt.readTreeAsValue(required(ctxt, node, "travelTime"), Duration.class);
      JsonNode dwellNode = node.get("dwellAtEnd");
      Duration dwell =
          dwellNode == null || dwellNode.isNull()
              ? Duration.ZERO
              : ctxt.readTreeAsValue(dwellNode, Duration.class);
      try {
        return new RouteSegment(from, to, km, travel, dwell);
      } catch (IllegalArgumentException e) {
        return ctxt.reportInputMismatch(this, "invalid route segment: %s", e.getMessage());
      }
    }

    private static JsonNode required(DeserializationContext ctxt, JsonNode node, String field)
        throws IOException {
      JsonNode value = node.get(field);
      if (value == null || value.isNull()) {
        return ctxt.reportInputMismatch(RouteSegment.class, "route segment: missing '%s'", field);
      }
      return value;
    }

    private static City city(DeserializationContext ctxt, String id) throws IOException {
      @SuppressWarnings("unchecked")
      Map<String, City> cities = (Map<String, City>) ctxt.getAttribute(JsonCodec.CITIES_ATTRIBUTE);
      if (cities == null) {
        return ctxt.reportInputMismatch(
            RouteSegment.class, "routes need a city catalogue: use JsonCodec.withCities(...)");
      }
      City city = cities.get(id);
      if (city == null) {
        return ctxt.reportInputMismatch(RouteSegment.class, "unknown city '%s'", id);
      }
      return city;
    }
  }
}
