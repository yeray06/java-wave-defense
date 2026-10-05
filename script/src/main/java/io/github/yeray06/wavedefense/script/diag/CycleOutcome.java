package io.github.yeray06.wavedefense.script.diag;

/** How one execution of a script ended. */
public enum CycleOutcome {
  /** Ran to the end. */
  COMPLETED,
  /** Stopped at a reload call; the turret is now busy reloading. */
  RELOADING,
  /** Raised a game exception; see the script's fault type and line. */
  FAULT
}
