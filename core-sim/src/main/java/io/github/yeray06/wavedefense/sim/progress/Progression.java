package io.github.yeray06.wavedefense.sim.progress;

import io.github.yeray06.wavedefense.sim.data.BalanceConfig;
import io.github.yeray06.wavedefense.sim.data.Challenge;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.WaveTable;
import io.github.yeray06.wavedefense.sim.world.WaveReport;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies a finished wave to the player's progress (D-57): a star for each goal met the first time
 * (beat it, beat it without leaks, meet its code challenge), a trophy for a boss's first kill, and
 * the plan's advance, repeat or defeat. Replaying a wave pays nothing more.
 */
public final class Progression {
  private final BalanceConfig balance;
  private final WaveTable waves;

  public Progression(GameData data) {
    this.balance = data.balance();
    this.waves = data.waves();
  }

  /**
   * @param memoryUsed the most memory lines any turret's code took, for the code challenge
   */
  public WaveOutcome apply(Progress progress, WaveReport report, int memoryUsed) {
    int wave = report.wave();
    if (wave != progress.currentWave) {
      throw new IllegalArgumentException(
          "report is for wave " + wave + " but the player is at wave " + progress.currentWave);
    }
    Challenge challenge = waves.wave(wave).challenge();
    boolean challengeMet = ChallengeJudge.met(challenge, report, memoryUsed);
    List<Goal> newGoals = new ArrayList<>();
    if (report.won()) {
      earn(progress, wave, Goal.CLEAR, true, newGoals);
      earn(progress, wave, Goal.PERFECT, report.perfect(), newGoals);
      earn(progress, wave, Goal.CHALLENGE, challengeMet, newGoals);
    }
    int trophies = 0;
    if (report.bossKilled() && !progress.bossesKilled.get(wave)) {
      progress.bossesKilled.set(wave);
      progress.trophies++;
      trophies = 1;
    }

    if (report.won()) {
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
        wave,
        report.won(),
        report.perfect(),
        challengeMet,
        ChallengeJudge.measure(challenge, report, memoryUsed),
        newGoals,
        trophies,
        progress.currentWave);
  }

  private static void earn(
      Progress progress, int wave, Goal goal, boolean met, List<Goal> newGoals) {
    if (met && !progress.met(wave, goal)) {
      switch (goal) {
        case CLEAR -> progress.beatenWaves.set(wave);
        case PERFECT -> progress.perfectWaves.set(wave);
        case CHALLENGE -> progress.challengeWaves.set(wave);
      }
      progress.stars++;
      newGoals.add(goal);
    }
  }
}
