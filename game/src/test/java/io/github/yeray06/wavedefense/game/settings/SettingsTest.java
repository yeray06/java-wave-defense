package io.github.yeray06.wavedefense.game.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SettingsTest {
  @Test
  void roundTripsThroughJson() {
    Settings settings =
        Settings.DEFAULTS.withFullscreen(true).withUiScale(150).withCodeFontSize(18);
    assertEquals(settings, Settings.fromJson(settings.toJson()));
    assertEquals(1.5f, settings.uiScale());
  }

  @Test
  void rejectsValuesThatAreNotOnOfferAndBrokenFiles() {
    assertThrows(IllegalArgumentException.class, () -> Settings.DEFAULTS.withUiScale(130));
    assertThrows(IllegalArgumentException.class, () -> Settings.DEFAULTS.withCodeFontSize(99));
    String json = Settings.DEFAULTS.toJson();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Settings.fromJson(json.replace("\"uiScalePercent\" : 100", "\"uiScalePercent\" : 7")));
    assertThrows(IllegalArgumentException.class, () -> Settings.fromJson("{"));
    assertThrows(
        IllegalArgumentException.class,
        () -> Settings.fromJson(json.replace("\"version\" : 1", "\"version\" : 2")));
  }
}
