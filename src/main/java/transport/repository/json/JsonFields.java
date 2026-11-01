package transport.repository.json;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/** Перевірки полів для власних десеріалізаторів, де FAIL_ON_UNKNOWN_PROPERTIES не діє. */
final class JsonFields {

  private JsonFields() {}

  static void checkKnown(DeserializationContext ctxt, JsonNode node, Class<?> type,
      Set<String> allowed) throws IOException {
    if (!node.isObject()) {
      ctxt.reportInputMismatch(type, "%s: expected JSON object", type.getSimpleName());
    }
    for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
      String field = it.next();
      if (!allowed.contains(field)) {
        ctxt.reportInputMismatch(type, "%s: unknown field '%s'", type.getSimpleName(), field);
      }
    }
  }

  static JsonNode required(DeserializationContext ctxt, JsonNode node, Class<?> type, String field)
      throws IOException {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return ctxt.reportInputMismatch(type, "%s: missing '%s'", type.getSimpleName(), field);
    }
    return value;
  }

  static String requiredText(DeserializationContext ctxt, JsonNode node, Class<?> type,
      String field) throws IOException {
    JsonNode value = required(ctxt, node, type, field);
    if (!value.isTextual()) {
      return ctxt.reportInputMismatch(type, "%s: '%s' must be a string", type.getSimpleName(),
          field);
    }
    return value.asText();
  }

  static int requiredInt(DeserializationContext ctxt, JsonNode node, Class<?> type, String field)
      throws IOException {
    JsonNode value = required(ctxt, node, type, field);
    if (!value.isInt()) {
      return ctxt.reportInputMismatch(type, "%s: '%s' must be an integer", type.getSimpleName(),
          field);
    }
    return value.intValue();
  }

  static double requiredNumber(DeserializationContext ctxt, JsonNode node, Class<?> type,
      String field) throws IOException {
    JsonNode value = required(ctxt, node, type, field);
    if (!value.isNumber()) {
      return ctxt.reportInputMismatch(type, "%s: '%s' must be a number", type.getSimpleName(),
          field);
    }
    return value.doubleValue();
  }
}
