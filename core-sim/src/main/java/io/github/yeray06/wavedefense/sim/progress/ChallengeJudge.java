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
    if (!report.won()) {
      return false;
    }
    return switch (challenge.type()) {
      case NO_AIR_SHOTS -> sum(report, WaveReport.TurretReport::airShots) == 0;
      case NO_FAULTS -> sum(report, WaveReport.TurretReport::faultCount) == 0;
      case NO_MANUAL_RELOADS -> sum(report, WaveReport.TurretReport::manualReloads) == 0;
      case MAX_MEMORY -> memoryUsed <= challenge.limit();
      case MAX_SHOTS -> sum(report, WaveReport.TurretReport::shots) <= challenge.limit();
      case NO_WASTED_SHOTS -> sum(report, WaveReport.TurretReport::wastedOnDead) == 0;
    };
  }

  private static int sum(WaveReport report, ToIntFunction<WaveReport.TurretReport> what) {
    return report.turrets().stream().mapToInt(what).sum();
  }
}
