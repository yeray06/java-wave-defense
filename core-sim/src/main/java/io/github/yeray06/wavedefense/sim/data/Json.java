package io.github.yeray06.wavedefense.sim.data;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** Strict JSON reading for data files: unknown fields and nulls for numbers are errors. */
final class Json {
  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
          .build();

  private Json() {}

  static <T> T read(String json, Class<T> type, String file) {
    try {
      return MAPPER.readValue(json, type);
    } catch (JacksonException e) {
      throw new DataException(file, e.getOriginalMessage(), e);
    }
  }
}
