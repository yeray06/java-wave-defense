package io.github.yeray06.wavedefense.sim.progress;

import io.github.yeray06.wavedefense.sim.data.BalanceConfig;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.economy.Formulas;
import io.github.yeray06.wavedefense.sim.world.WaveReport;

/** Applies a finished wave to the player's progress: pay, bonuses, advance, repeat or defeat. */
public final class Progression {
  private final BalanceConfig balance;
  private final Formulas formulas;

  public Progression(GameData data) {
    this.balance = data.balance();
    this.formulas = new Formulas(balance);
  }

  public WaveOutcome apply(Progress progress, WaveReport report) {
    int wave = report.wave();
    if (wave != progress.currentWave) {
      throw new IllegalArgumentException(
          "report is for wave " + wave + " but the player is at wave " + progress.currentWave);
    }
    long kills = report.earnedCents();
    long perfectBonus = report.perfect() ? Math.round(kills * balance.perfectWaveBonus()) : 0;
    long bossBonus = report.bossKilled() ? bossBonus(progress, wave) : 0;
    progress.moneyCents += kills + perfectBonus + bossBonus;

    if (report.won()) {
      progress.beatenWaves.set(wave);
      if (report.perfect()) {
        progress.perfectWaves.set(wave);
      }
      if (progress.autoAdvance && wave < balance.lastWave()) {
        progress.currentWave = wave + 1;
      }
    } else {
      // Plan, "Derrota y reinicio": back one wave, auto-advance off, nothing else lost.
      progress.currentWave = Math.max(1, wave - 1);
      progress.autoAdvance = false;
    }
    progress.highestWaveReached = Math.max(progress.highestWaveReached, progress.currentWave);
    return new WaveOutcome(
        wave, report.won(), report.perfect(), kills, perfectBonus, bossBonus, progress.currentWave);
  }

  /** First kill: the pay of several whole waves; later kills: a number of normal enemies. */
  private long bossBonus(Progress progress, int wave) {
    BalanceConfig.BossRule rule = balance.boss();
    if (!progress.bossesKilled.get(wave)) {
      progress.bossesKilled.set(wave);
      return rule.firstKillRewardInWaves() * formulas.waveRewardCents(wave);
    }
    return rule.repeatKillRewardInEnemies() * formulas.rewardPerEnemyCents(wave);
  }
}
