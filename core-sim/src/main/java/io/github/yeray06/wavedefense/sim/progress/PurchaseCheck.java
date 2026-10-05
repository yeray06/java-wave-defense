package io.github.yeray06.wavedefense.sim.progress;

/** Whether something can be bought, and if not, why. Shown to the player via i18n. */
public enum PurchaseCheck {
  OK,
  NOT_IN_MVP,
  ALREADY_OWNED,
  MISSING_REQUIREMENT,
  MAX_LEVEL,
  NOT_ENOUGH_MONEY
}
