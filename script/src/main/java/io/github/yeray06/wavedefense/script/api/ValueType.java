package io.github.yeray06.wavedefense.script.api;

/** Types that cross the boundary between a script and the turret API. */
public enum ValueType {
  INT,
  BOOLEAN,
  ENEMY,
  /** The enemies in range, as a {@code List<Enemigo>}. */
  ENEMY_LIST,
  AMMO,
  /** Any value that can be printed: {@code int}, {@code boolean}, an enemy, a list or null. */
  PRINTABLE,
  VOID
}
