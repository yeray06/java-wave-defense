package io.github.yeray06.wavedefense.script.api;

import java.util.EnumSet;
import java.util.Set;

/** What the player owns right now: bought syntax and the highest wave reached. */
public record Unlocks(Set<Feature> features, int highestWaveReached) {

  public Unlocks {
    features = features.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(features));
  }

  public static Unlocks of(int highestWaveReached, Feature... features) {
    return new Unlocks(Set.of(features), highestWaveReached);
  }

  public boolean has(Feature feature) {
    return features.contains(feature);
  }

  public boolean satisfies(Requirement requirement) {
    return switch (requirement) {
      case Requirement.Free free -> true;
      case Requirement.NeedsFeature needs -> has(needs.feature());
      case Requirement.NeedsWave needs -> highestWaveReached >= needs.wave();
    };
  }
}
