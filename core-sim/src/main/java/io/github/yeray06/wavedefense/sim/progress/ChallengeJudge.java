package io.github.yeray06.wavedefense.sim.progress;

import io.github.yeray06.wavedefense.sim.data.Challenge;
import io.github.yeray06.wavedefense.sim.world.WaveReport;
import java.util.function.ToIntFunction;

/** Whether a won wave met its code challenge (D-57). */
public final class ChallengeJudge {
  private ChallengeJudge() {}

  /**
   * @param memoryUsed the most memory lines any turret's code took in the wave
   */
  public static boolean met(Challenge challenge, WaveReport report, int memoryUsed) {
    return report.won() && measure(challenge, report, memoryUsed) <= challenge.limit();
  }

  /**
   * What the challenge counts in this wave: air shots, exceptions, manual reloads, memory lines,
   * shots or wasted bullets. It is met when this is at most the challenge's limit (0 for the "no…"
   * challenges).
   */
  public static int measure(Challenge challenge, WaveReport report, int memoryUsed) {
    return switch (challenge.type()) {
      case NO_AIR_SHOTS -> sum(report, WaveReport.TurretReport::airShots);
      case NO_FAULTS -> sum(report, WaveReport.TurretReport::faultCount);
      case NO_MANUAL_RELOADS -> sum(report, WaveReport.TurretReport::manualReloads);
      case MAX_MEMORY -> memoryUsed;
      case MAX_SHOTS -> sum(report, WaveReport.TurretReport::shots);
      case NO_WASTED_SHOTS -> sum(report, WaveReport.TurretReport::wastedOnDead);
    };
  }

  private static int sum(WaveReport report, ToIntFunction<WaveReport.TurretReport> what) {
    return report.turrets().stream().mapToInt(what).sum();
  }
}
