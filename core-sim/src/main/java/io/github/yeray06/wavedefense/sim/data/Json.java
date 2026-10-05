package io.github.yeray06.wavedefense.sim.data;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Strict JSON reading for data files: unknown fields, duplicate keys, missing fields and nulls for
 * numbers are all errors, so a typo never becomes a silent default.
 */
final class Json {
  private static final ObjectMapper LENIENT_FIELDS = mapper(false);
  private static final ObjectMapper STRICT = mapper(true);

  private Json() {}

  private static ObjectMapper mapper(boolean requireEveryField) {
    JsonMapper.Builder builder =
        JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    if (requireEveryField) {
      builder.enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
    }
    return builder.build();
  }

  /** Missing fields become null; the caller checks them. */
  static <T> T read(String json, Class<T> type, String file) {
    return read(LENIENT_FIELDS, json, type, file);
  }

  /** Every field of every record must be written. */
  static <T> T readStrict(String json, Class<T> type, String file) {
    return read(STRICT, json, type, file);
  }

  private static <T> T read(ObjectMapper mapper, String json, Class<T> type, String file) {
    try {
      return mapper.readValue(json, type);
    } catch (JacksonException e) {
      throw new DataException(file, describe(e), e);
    }
  }

  private static String describe(JacksonException e) {
    Throwable root = e;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String where = e instanceof JsonMappingException m ? m.getPathReference() : "";
    String what =
        root instanceof IllegalArgumentException ? root.getMessage() : e.getOriginalMessage();
    return where.isEmpty() ? what : what + " (at " + where + ")";
  }
}
