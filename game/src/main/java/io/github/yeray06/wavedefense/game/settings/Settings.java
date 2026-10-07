package io.github.yeray06.wavedefense.game.settings;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;

/**
 * The player's settings (phase 6, D-56), kept in {@code settings.json} apart from the save so a new
 * game does not reset them.
 *
 * @param version the file's format
 * @param fullscreen whether the game fills the screen
 * @param uiScalePercent size of the interface, one of {@link #UI_SCALES}
 * @param codeFontSize size in pixels of the code in the editor and the manual, one of {@link
 *     #CODE_SIZES}, before the interface scale
 */
public record Settings(int version, boolean fullscreen, int uiScalePercent, int codeFontSize) {
  public static final int VERSION = 1;

  /** Interface sizes on offer, in percent: for big and high-density screens (D-46). */
  public static final List<Integer> UI_SCALES = List.of(100, 125, 150, 175, 200);

  /** Code sizes on offer, in pixels; 14 is the size the editor had before settings existed. */
  public static final List<Integer> CODE_SIZES = List.of(12, 14, 16, 18, 20, 22);

  public static final Settings DEFAULTS = new Settings(VERSION, false, 100, 14);

  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .build();

  public Settings {
    if (version != VERSION) {
      throw new IllegalArgumentException("settings version " + version);
    }
    if (!UI_SCALES.contains(uiScalePercent)) {
      throw new IllegalArgumentException("interface size " + uiScalePercent);
    }
    if (!CODE_SIZES.contains(codeFontSize)) {
      throw new IllegalArgumentException("code size " + codeFontSize);
    }
  }

  /** The interface scale as a factor: 1.25 for 125 %. */
  public float uiScale() {
    return uiScalePercent / 100f;
  }

  public Settings withFullscreen(boolean on) {
    return new Settings(version, on, uiScalePercent, codeFontSize);
  }

  public Settings withUiScale(int percent) {
    return new Settings(version, fullscreen, percent, codeFontSize);
  }

  public Settings withCodeFontSize(int pixels) {
    return new Settings(version, fullscreen, uiScalePercent, pixels);
  }

  public String toJson() {
    try {
      return MAPPER.writeValueAsString(this);
    } catch (JacksonException e) {
      throw new IllegalStateException("cannot write settings", e);
    }
  }

  /**
   * @throws IllegalArgumentException for a file that is not valid settings
   */
  public static Settings fromJson(String json) {
    try {
      return MAPPER.readValue(json, Settings.class);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("broken settings: " + e.getOriginalMessage(), e);
    }
  }
}
