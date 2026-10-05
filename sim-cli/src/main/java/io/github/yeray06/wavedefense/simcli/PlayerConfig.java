package io.github.yeray06.wavedefense.simcli;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The reference player's assumptions ({@code reference/player.json}). None comes from the plan;
 * they model a human and are reported as such.
 *
 * @param manualReloadReactionSeconds how long a player takes to click reload on an empty turret
 * @param maxWavesPlayed gives up after this many waves (a wall that never falls)
 */
record PlayerConfig(
    List<String> notFromPlan,
    double manualReloadReactionSeconds,
    int maxWavesPlayed,
    Search healthScaleSearch) {

  /** Bisection bounds for the double rule's health multiplier. */
  record Search(double min, double max, int iterations) {}

  static PlayerConfig load() {
    try (InputStream in = PlayerConfig.class.getResourceAsStream("/reference/player.json")) {
      if (in == null) {
        throw new IllegalStateException("missing reference/player.json");
      }
      return JsonMapper.builder()
          .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .build()
          .readValue(in, PlayerConfig.class);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
