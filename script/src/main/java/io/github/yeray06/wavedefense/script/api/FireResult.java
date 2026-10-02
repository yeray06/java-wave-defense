package io.github.yeray06.wavedefense.script.api;

/** What happened when a script called {@code fire()}. */
public enum FireResult {
  FIRED,
  /** The magazine was empty: the script gets a no-ammo exception. */
  NO_AMMO,
  /** Heat was already at the maximum: the script gets an overheat exception. */
  OVERHEATED
}
