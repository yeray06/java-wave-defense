package io.github.yeray06.wavedefense.sim.world;

/** What happened in an instant, for the render's effects (D-40). */
public enum BattleEventType {
  /** A turret fired at an enemy. {@code a} = turret, {@code b} = ammo type ordinal. */
  SHOT,
  /** A turret fired with nothing in range (D-05). {@code a} = turret. */
  AIR_SHOT,
  /** A bullet hit. {@code a} = enemy kind ordinal, {@code b} = 1 if armor reduced the damage. */
  HIT,
  /** An enemy died. {@code a} = enemy kind ordinal. */
  KILL,
  /** An enemy reached the core. {@code a} = enemy kind ordinal. */
  LEAK,
  /** A turret's script threw. {@code a} = turret, {@code b} = fault type ordinal. */
  FAULT,
  /** A turret started reloading. {@code a} = turret, {@code b} = 1 if it was the player's click. */
  RELOAD,
  /** A turret's magazine reached zero. {@code a} = turret. */
  EMPTY
}
