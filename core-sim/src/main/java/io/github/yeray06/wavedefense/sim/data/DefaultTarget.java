package io.github.yeray06.wavedefense.sim.data;

/**
 * Whom {@code disparar()} hits when the script has not aimed (D-31). {@code enemigoMasCercano()}
 * always means nearest to the turret, whatever this says.
 */
public enum DefaultTarget {
  NEAREST_TO_TURRET,
  NEAREST_TO_CORE,
  /** A different enemy in range on every shot, from the battle's seeded generator. */
  RANDOM_IN_RANGE
}
