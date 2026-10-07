package io.github.yeray06.wavedefense.sim.progress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.sim.data.Challenge;
import io.github.yeray06.wavedefense.sim.data.Challenge.Type;
import io.github.yeray06.wavedefense.sim.world.WaveReport;
import io.github.yeray06.wavedefense.sim.world.WaveReport.TurretReport;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChallengeJudgeTest {
  private static final TurretReport CLEAN = new TurretReport(30, 0, 0, 3, 0, 0, List.of());

  private static WaveReport report(boolean won, TurretReport... turrets) {
    return new WaveReport(1, won, 8, 8, List.of(), false, false, 600, List.of(turrets));
  }

  private static boolean met(Type type, int limit, TurretReport turret, int memory) {
    return ChallengeJudge.met(new Challenge(type, limit), report(true, CLEAN, turret), memory);
  }

  @Test
  void aLostWaveNeverMeetsItsChallenge() {
    assertFalse(ChallengeJudge.met(new Challenge(Type.NO_AIR_SHOTS, 0), report(false, CLEAN), 1));
  }

  @Test
  void eachChallengeLooksAtItsOwnCountOverEveryTurret() {
    assertTrue(met(Type.NO_AIR_SHOTS, 0, CLEAN, 12));
    assertFalse(met(Type.NO_AIR_SHOTS, 0, new TurretReport(30, 1, 0, 3, 0, 0, List.of()), 12));

    TurretReport faulty =
        new TurretReport(
            30, 0, 0, 3, 0, 0, List.of(new WaveReport.FaultCount(FaultType.NO_AMMO, 1, 1)));
    assertTrue(met(Type.NO_FAULTS, 0, CLEAN, 12));
    assertFalse(met(Type.NO_FAULTS, 0, faulty, 12));

    assertTrue(met(Type.NO_MANUAL_RELOADS, 0, CLEAN, 12));
    assertFalse(met(Type.NO_MANUAL_RELOADS, 0, new TurretReport(30, 0, 0, 3, 1, 0, List.of()), 12));

    assertTrue(met(Type.NO_WASTED_SHOTS, 0, CLEAN, 12));
    assertFalse(met(Type.NO_WASTED_SHOTS, 0, new TurretReport(30, 0, 2, 3, 0, 0, List.of()), 12));
  }

  @Test
  void limitsAreInclusive() {
    assertTrue(met(Type.MAX_MEMORY, 4, CLEAN, 4));
    assertFalse(met(Type.MAX_MEMORY, 4, CLEAN, 5));
    assertTrue(met(Type.MAX_SHOTS, 60, CLEAN, 12), "30 + 30 shots");
    assertFalse(met(Type.MAX_SHOTS, 59, CLEAN, 12));
  }
}
