package io.github.yeray06.wavedefense.sim.data;

/** Enemy types. Display names live in i18n. */
public enum EnemyKind {
  BASIC,
  REGENERATOR,
  ARMORED,
  SWARM,
  /** Three times as fast as a basic enemy: slips past while the turret shoots another (act 2). */
  RUNNER,
  /** Weak to one ammo, a different one each wave: the library changes it in one place (act 2). */
  MUTANT,
  BOSS
}
