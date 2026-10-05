package io.github.yeray06.wavedefense.sim.world;

/** Hardware levels bought; hardware is global to every turret (D-13). */
public record Loadout(int damageLevel, int magazineLevel, int heatSinkLevel) {
  public static final Loadout NONE = new Loadout(0, 0, 0);

  public Loadout {
    if (damageLevel < 0 || magazineLevel < 0 || heatSinkLevel < 0) {
      throw new IllegalArgumentException("levels cannot be negative");
    }
  }
}
