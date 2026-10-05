package io.github.yeray06.wavedefense.script.diag;

/**
 * Game exceptions a script can raise at run time. Display names live in {@code
 * i18n/messages.properties} under {@code script.fault.<NAME>}.
 */
public enum FaultType {
  NO_AMMO,
  OVERHEAT,
  NULL_POINTER,
  INFINITE_LOOP,
  ARITHMETIC,
  /** A bug in the interpreter or the simulation, not in the player's code. */
  INTERNAL_ERROR
}
