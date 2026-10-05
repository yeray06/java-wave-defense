package io.github.yeray06.wavedefense.script.api;

/** What the player needs before a call compiles. */
public sealed interface Requirement {

  /** Available from the start. */
  record Free() implements Requirement {}

  /** Needs a syntax piece from the shop. */
  record NeedsFeature(Feature feature) implements Requirement {
    public NeedsFeature {
      if (feature == null) {
        throw new IllegalArgumentException("feature is required");
      }
    }
  }

  /** Needs the player to have reached a wave. */
  record NeedsWave(int wave) implements Requirement {
    public NeedsWave {
      if (wave < 1) {
        throw new IllegalArgumentException("wave must be at least 1: " + wave);
      }
    }
  }
}
