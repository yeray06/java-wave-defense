package io.github.yeray06.wavedefense.sim.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.sim.TestData;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.world.WaveReport;
import java.util.List;
import org.junit.jupiter.api.Test;

/** D-63: records, the streak, achievements, hats and names. */
class CareerTest {

  private static GameData data() {
    return TestData.load();
  }

  private static WaveReport report(int wave, boolean won, int ticks, int shots) {
    return report(wave, won, ticks, shots, 0, List.of());
  }

  private static WaveReport report(
      int wave,
      boolean won,
      int ticks,
      int shots,
      int greetings,
      List<WaveReport.FaultCount> faults) {
    return new WaveReport(
        wave,
        won,
        10,
        won ? 10 : 5,
        List.of(),
        false,
        false,
        ticks,
        List.of(new WaveReport.TurretReport(shots, 0, 0, 0, 0, 0, faults, greetings)));
  }

  @Test
  void eachMarkKeepsTheBestOfAnyWonRun() {
    Career career = Career.newGame(data(), false);
    Progress progress = Progress.newGame(data());
    CareerOutcome first = career.apply(progress, report(3, true, 900, 40), 6);
    assertEquals(3, first.newMarks().size(), "a first win sets every mark");
    CareerOutcome second = career.apply(progress, report(3, true, 1000, 30), 7);
    assertEquals(java.util.Set.of(WaveRecord.Mark.SHOTS), second.newMarks());
    assertEquals(new WaveRecord(900, 30, 6), career.record(3));
    career.apply(progress, report(4, false, 10, 1), 1);
    assertNull(career.record(4), "a defeat sets no record");
  }

  @Test
  void theStreakGrowsOnLaterWavesAndItsScoreIsSuperlinear() {
    Career career = Career.newGame(data(), false);
    Progress progress = Progress.newGame(data());
    assertEquals(1, career.apply(progress, report(1, true, 1, 1), 1).streakPoints());
    assertEquals(4, career.apply(progress, report(2, true, 1, 1), 1).streakPoints());
    assertEquals(9, career.apply(progress, report(3, true, 1, 1), 1).streakPoints());
    assertEquals(3, career.streak());
    assertEquals(14, career.streakScore());

    CareerOutcome replay = career.apply(progress, report(3, true, 1, 1), 1);
    assertEquals(1, replay.streak(), "replaying a wave starts a new streak");
    assertEquals(3, career.streakScore());
    career.apply(progress, report(4, false, 1, 1), 1);
    assertEquals(0, career.streak());
    assertEquals(0, career.streakScore());
    assertEquals(3, career.bestStreak());
    assertEquals(14, career.bestStreakScore());
  }

  @Test
  void achievementsUnlockOnceFromWhatTheGameDid() {
    GameData data = data();
    Career career = Career.newGame(data, false);
    Progress progress = Progress.newGame(data);
    progress.beatenWaves.set(1);
    var faults = List.of(new WaveReport.FaultCount(FaultType.STACK_OVERFLOW, 2, true, 1));
    CareerOutcome outcome = career.apply(progress, report(1, true, 5, 5, 1, faults), 1);
    assertTrue(
        outcome.newAchievements().containsAll(List.of("FIRST_WAVE", "HELLO", "TO_INFINITY")));
    assertFalse(outcome.newAchievements().contains("NULL_POINTER"));
    assertTrue(
        career.apply(progress, report(1, true, 5, 5, 1, faults), 1).newAchievements().isEmpty(),
        "never twice");
  }

  @Test
  void collectorNeedsEveryOtherAchievement() {
    GameData data = data();
    Career career = Career.newGame(data, false);
    var collector = data.career().achievement("COLLECTOR");
    assertEquals(data.career().achievements().size() - 1, career.target(collector));
    assertFalse(career.unlocked("COLLECTOR"));
  }

  @Test
  void hatsNeedTheirAchievementAndNamesAreCleaned() {
    GameData data = data();
    Career career = Career.newGame(data, false);
    assertFalse(career.setHat(0, "CAP"), "no such achievement");
    assertFalse(career.setHat(0, "FIRST_WAVE"), "not unlocked yet");
    Progress progress = Progress.newGame(data);
    progress.beatenWaves.set(1);
    career.unlock(progress);
    assertFalse(career.setHat(0, "HELLO"), "an achievement without a hat");
    assertTrue(career.setHat(0, "FIRST_WAVE"));
    assertEquals("FIRST_WAVE", career.hat(0));
    assertTrue(career.setHat(0, ""));

    assertEquals("Ñandú-1", career.setName(1, "  Ñandú-1\n"));
    assertEquals(data.career().turretNameMaxLength(), career.setName(2, "x".repeat(99)).length());
    assertEquals("", career.setName(3, "<<>>"));
  }

  @Test
  void anExpertGameOwnsEverySyntaxPieceOnSale() {
    GameData data = data();
    Progress expert = Progress.newExpertGame(data);
    for (var item : data.shop().syntax()) {
      assertEquals(item.inMvp(), expert.owns(item.feature()), item.feature().toString());
    }
    assertEquals(Progress.newGame(data).stars(), expert.stars());
    WaveLoop loop = new WaveLoop(data, expert, Career.newGame(data, true));
    assertTrue(loop.career().expert());
    assertTrue(loop.compile("for (int i = 0; i < 2; i++) disparar();").succeeded());
    assertTrue(expert.owns(Feature.METHODS));
  }

  @Test
  void aTrialPlaysAReachedWaveWithoutTouchingTheGame() {
    GameData data = data();
    WaveLoop loop = new WaveLoop(data, Progress.newGame(data));
    var before = loop.progress().snapshot();
    long revision = loop.revision();
    long battles = loop.battlesBuilt();
    WaveReport trial = loop.trial(1);
    assertEquals(1, trial.wave());
    assertTrue(trial.won(), "disparar() and quick hand reloads beat wave 1");
    assertEquals(before, loop.progress().snapshot());
    assertEquals(revision, loop.revision());
    assertEquals(battles, loop.battlesBuilt());
    assertNull(loop.career().record(1), "a trial sets no record");
    assertNull(loop.trial(2), "a wave not reached yet");
  }
}
