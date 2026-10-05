package io.github.yeray06.wavedefense.sim.world;

import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything a wave needs besides data.
 *
 * @param scripts one per built turret, in slot order; {@code null} leaves that turret idle
 * @param healthScale multiplies every enemy's health: 1 in the game, varied by the balance tools
 */
public record BattleSetup(
    int wave, List<CompiledScript> scripts, Loadout loadout, double healthScale) {

  public BattleSetup {
    if (wave < 1) {
      throw new IllegalArgumentException("wave must be at least 1");
    }
    if (scripts.isEmpty()) {
      throw new IllegalArgumentException("at least one turret is needed");
    }
    scripts = Collections.unmodifiableList(new ArrayList<>(scripts));
    if (!(healthScale > 0)) {
      throw new IllegalArgumentException("healthScale must be positive");
    }
  }
}
