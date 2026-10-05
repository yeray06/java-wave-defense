package io.github.yeray06.wavedefense.sim.economy;

/**
 * Money is kept exactly in hundredths ("cents") in a {@code long}: the plan's formulas give values
 * like 2.645, and doubles would drift after hundreds of farmed waves (D-11).
 */
public final class Money {
  public static final long CENTS_PER_UNIT = 100;

  private Money() {}

  /** Rounds a plan amount (in game units) to the nearest cent, halves up. */
  public static long cents(double units) {
    return Math.round(units * CENTS_PER_UNIT);
  }

  /** What the UI shows: whole units, rounded down. */
  public static long wholeUnits(long cents) {
    return Math.floorDiv(cents, CENTS_PER_UNIT);
  }

  public static double units(long cents) {
    return cents / (double) CENTS_PER_UNIT;
  }
}
