package io.github.yeray06.wavedefense.sim.economy;

import io.github.yeray06.wavedefense.sim.data.BalanceConfig;

/**
 * The plan's wave formulas ("Economía y farmeo"), with every constant taken from data. Its money
 * formulas are gone with the money (D-57).
 */
public final class Formulas {
  private final BalanceConfig balance;

  public Formulas(BalanceConfig balance) {
    this.balance = balance;
  }

  /** {@code vida(n) = 10 · 1,18^(n−1)}: health of a normal enemy of wave {@code n}. */
  public double enemyHealth(int wave) {
    return grow(balance.enemyHealth(), wave);
  }

  /** {@code enemigos(n) = min(6 + 2n, 60)}. */
  public int enemyCount(int wave) {
    BalanceConfig.CountRule rule = balance.enemyCount();
    return Math.min(rule.base() + rule.perWave() * wave, rule.max());
  }

  private static double grow(BalanceConfig.Growth growth, int wave) {
    if (wave < 1) {
      throw new IllegalArgumentException("waves start at 1: " + wave);
    }
    return growth.base() * StrictMath.pow(growth.growthPerWave(), wave - 1);
  }
}
