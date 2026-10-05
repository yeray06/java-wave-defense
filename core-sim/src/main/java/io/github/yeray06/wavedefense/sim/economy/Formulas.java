package io.github.yeray06.wavedefense.sim.economy;

import io.github.yeray06.wavedefense.sim.data.BalanceConfig;
import io.github.yeray06.wavedefense.sim.data.ShopConfig;

/** The plan's formulas ("Economía y farmeo"), with every constant taken from data. */
public final class Formulas {
  private final BalanceConfig balance;

  public Formulas(BalanceConfig balance) {
    this.balance = balance;
  }

  /** {@code vida(n) = 10 · 1,18^(n−1)}: health of a normal enemy of wave {@code n}. */
  public double enemyHealth(int wave) {
    return grow(balance.enemyHealth(), wave);
  }

  /** {@code dinero por enemigo(n) = 2 · 1,15^(n−1)}, in cents. */
  public long rewardPerEnemyCents(int wave) {
    return Money.cents(grow(balance.rewardPerEnemy(), wave));
  }

  /** {@code enemigos(n) = min(6 + 2n, 60)}. */
  public int enemyCount(int wave) {
    BalanceConfig.CountRule rule = balance.enemyCount();
    return Math.min(rule.base() + rule.perWave() * wave, rule.max());
  }

  /** The plan's "dinero por oleada": every normal enemy killed, without bonuses. */
  public long waveRewardCents(int wave) {
    return enemyCount(wave) * rewardPerEnemyCents(wave);
  }

  /** Cost of buying the next level when {@code ownedLevels} are already bought. */
  public static long hardwareCostCents(ShopConfig.HardwareItem item, int ownedLevels) {
    return Money.cents(item.firstCost() * StrictMath.pow(item.costGrowth(), ownedLevels));
  }

  private static double grow(BalanceConfig.Growth growth, int wave) {
    if (wave < 1) {
      throw new IllegalArgumentException("waves start at 1: " + wave);
    }
    return growth.base() * StrictMath.pow(growth.growthPerWave(), wave - 1);
  }
}
