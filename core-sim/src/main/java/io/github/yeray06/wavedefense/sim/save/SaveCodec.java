package io.github.yeray06.wavedefense.sim.save;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Saved games to and from JSON. Every file carries {@code "version"}; an older one is brought up to
 * date by the migrations, one version at a time, before it is read. Strict: unknown or missing
 * fields are errors, never silent defaults.
 */
public final class SaveCodec {
  /** The format this game writes. Raise it, and add a migration from the old one, on any change. */
  public static final int VERSION = 2;

  /** Turns a file of version {@link #from()} into version {@code from() + 1}. */
  public interface Migration {
    int from();

    void apply(ObjectNode save);
  }

  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .build();

  private final int version;
  private final Map<Integer, Migration> migrations = new HashMap<>();

  /**
   * @param version the format written and read
   * @param migrations one per older version
   */
  public SaveCodec(int version, List<Migration> migrations) {
    this.version = version;
    for (Migration migration : migrations) {
      if (migration.from() >= version || this.migrations.put(migration.from(), migration) != null) {
        throw new IllegalArgumentException("bad migration from " + migration.from());
      }
    }
  }

  /** The game's codec: the current version and every migration to it. */
  public static SaveCodec standard() {
    return new SaveCodec(VERSION, List.of(new MoneyToStars()));
  }

  public String write(SaveGame save) {
    if (save.version() != version) {
      throw new IllegalArgumentException("writes version " + version + ", got " + save.version());
    }
    try {
      return MAPPER.writeValueAsString(save);
    } catch (JacksonException e) {
      throw new IllegalStateException("cannot write a save", e);
    }
  }

  /**
   * @throws IllegalArgumentException for anything that is not a save this game can read: broken
   *     JSON, a newer version, a missing migration or a field out of place
   */
  public SaveGame read(String json) {
    try {
      JsonNode tree = MAPPER.readTree(json);
      if (!(tree instanceof ObjectNode save) || !save.path("version").isInt()) {
        throw new IllegalArgumentException("not a save: no version");
      }
      int found = save.get("version").intValue();
      if (found > version) {
        throw new IllegalArgumentException(
            "save from a newer game: version " + found + ", this one reads " + version);
      }
      for (int v = found; v < version; v++) {
        Migration migration = migrations.get(v);
        if (migration == null) {
          throw new IllegalArgumentException("no migration from version " + v);
        }
        migration.apply(save);
        save.put("version", v + 1);
      }
      return MAPPER.treeToValue(save, SaveGame.class);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("broken save: " + e.getOriginalMessage(), e);
    }
  }
}
