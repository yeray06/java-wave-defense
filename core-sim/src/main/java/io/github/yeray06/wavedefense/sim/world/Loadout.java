package io.github.yeray06.wavedefense.sim.world;

/**
 * Hardware levels a battle uses; hardware is global to every turret (D-13). Memory is not here: it
 * limits the code when it compiles, not the battle.
 */
public record Loadout(int damageLevel, int magazineLevel, int heatSinkLevel, int processorLevel) {
  public static final Loadout NONE = new Loadout(0, 0, 0, 0);

  public Loadout {
    if (damageLevel < 0 || magazineLevel < 0 || heatSinkLevel < 0 || processorLevel < 0) {
      throw new IllegalArgumentException("levels cannot be negative");
    }
  }

  /** Without a processor: the MVP's three upgrades. */
  public Loadout(int damageLevel, int magazineLevel, int heatSinkLevel) {
    this(damageLevel, magazineLevel, heatSinkLevel, 0);
  }
}
