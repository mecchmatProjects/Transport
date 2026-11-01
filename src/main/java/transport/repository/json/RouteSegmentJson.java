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
import java.util.Set;
import transport.model.City;
import transport.model.RouteSegment;

/** У JSON міста записуються за id і при читанні беруться з каталогу міст. */
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
    private static final Set<String> FIELDS =
        Set.of("from", "to", "distanceKm", "travelTime", "dwellAtEnd");

    Deserializer() {
      super(RouteSegment.class);
    }

    @Override
    public RouteSegment deserialize(JsonParser parser, DeserializationContext ctxt)
        throws IOException {
      JsonNode node = ctxt.readTree(parser);
      JsonFields.checkKnown(ctxt, node, RouteSegment.class, FIELDS);
      City from = city(ctxt, JsonFields.requiredText(ctxt, node, RouteSegment.class, "from"));
      City to = city(ctxt, JsonFields.requiredText(ctxt, node, RouteSegment.class, "to"));
      double km = JsonFields.requiredNumber(ctxt, node, RouteSegment.class, "distanceKm");
      Duration travel = ctxt.readTreeAsValue(
          JsonFields.required(ctxt, node, RouteSegment.class, "travelTime"), Duration.class);
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
