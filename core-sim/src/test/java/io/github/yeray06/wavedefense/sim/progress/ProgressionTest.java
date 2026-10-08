package io.github.yeray06.wavedefense.sim.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.sim.TestData;
import io.github.yeray06.wavedefense.sim.data.Challenge;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.world.WaveReport;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** D-57: stars once per goal, boss trophies, and the plan's "Derrota y reinicio" and D-02. */
class ProgressionTest {
  private final GameData data = TestData.load();
  private final Progression progression = new Progression(data);
  private final Shop shop = new Shop(data);

  /** A turret that met every challenge: no air shots, faults, clicks or wasted bullets. */
  private static WaveReport.TurretReport clean(int shots) {
    return new WaveReport.TurretReport(shots, 0, 0, 1, 0, 0, List.of());
  }

  private static WaveReport report(
      int wave, boolean won, int leaks, boolean boss, WaveReport.TurretReport turret) {
    List<WaveReport.Leak> leakList =
        Collections.nCopies(leaks, new WaveReport.Leak(EnemyKind.BASIC, 1, -1));
    return new WaveReport(wave, won, 10, 10 - leaks, leakList, boss, false, 2700, List.of(turret));
  }

  private static WaveReport report(int wave, boolean won, int leaks) {
    return report(wave, won, leaks, false, clean(10));
  }

  private static Progress atWave(int wave) {
    Progress progress = Progress.newGame(TestData.load());
    progress.currentWave = wave;
    progress.highestWaveReached = wave;
    return progress;
  }

  @Test
  void newGameStartsAtWaveOneWithOneTurretNothingBoughtAndAutoAdvanceOn() {
    Progress progress = Progress.newGame(data);
    assertEquals(1, progress.currentWave());
    assertEquals(1, progress.turretCount());
    assertEquals(data.balance().startingStars(), progress.stars());
    assertEquals(0, progress.trophies());
    assertTrue(progress.autoAdvance());
    assertTrue(progress.features().isEmpty());
  }

  @Test
  void aPerfectWinThatMeetsTheChallengePaysThreeStarsOnce() {
    Progress progress = atWave(1);
    progress.setAutoAdvance(false);
    WaveOutcome outcome = progression.apply(progress, report(1, true, 0), 0);
    assertEquals(List.of(Goal.CLEAR, Goal.PERFECT, Goal.CHALLENGE), outcome.newGoals());
    assertEquals(3, progress.stars());
    assertTrue(progress.speedUpAllowed(1));

    WaveOutcome again = progression.apply(progress, report(1, true, 0), 0);
    assertEquals(0, again.stars(), "replaying pays nothing: no farming");
    assertTrue(again.challengeMet());
    assertEquals(3, progress.stars());
    assertEquals(3, progress.starsEarned());
  }

  @Test
  void missingGoalsCanBeEarnedOnALaterRun() {
    Progress progress = atWave(1);
    progress.setAutoAdvance(false);
    WaveReport.TurretReport airShots = new WaveReport.TurretReport(10, 3, 0, 1, 0, 0, List.of());
    WaveOutcome first = progression.apply(progress, report(1, true, 2, false, airShots), 0);
    assertEquals(List.of(Goal.CLEAR), first.newGoals());
    assertFalse(first.challengeMet(), "wave 1 asks for no air shots");
    assertFalse(progress.speedUpAllowed(1));

    WaveOutcome second = progression.apply(progress, report(1, true, 0), 0);
    assertEquals(List.of(Goal.PERFECT, Goal.CHALLENGE), second.newGoals());
    assertTrue(progress.met(1, Goal.CHALLENGE));
  }

  @Test
  void withAutoAdvanceOffTheSameWaveRepeats() {
    Progress progress = atWave(3);
    progress.setAutoAdvance(false);
    progression.apply(progress, report(3, true, 0), 0);
    assertEquals(3, progress.currentWave());
  }

  @Test
  void defeatPaysNothingGoesBackOneWaveAndKeepsEverything() {
    Progress progress = atWave(5);
    progress.stars = 4;
    progress.features.add(Feature.IF_ELSE);
    progress.levels.put(HardwareKind.DAMAGE, 2);
    WaveOutcome outcome = progression.apply(progress, report(5, false, 20), 0);
    assertEquals(0, outcome.stars());
    assertFalse(outcome.challengeMet(), "a lost wave meets no challenge");
    assertEquals(4, progress.stars());
    assertEquals(4, progress.currentWave());
    assertFalse(progress.autoAdvance());
    assertTrue(progress.owns(Feature.IF_ELSE), "syntax is kept");
    assertEquals(2, progress.level(HardwareKind.DAMAGE), "hardware is kept");
    assertEquals(5, progress.highestWaveReached());
  }

  @Test
  void losingWaveOneStaysOnWaveOne() {
    Progress progress = atWave(1);
    progression.apply(progress, report(1, false, 20), 0);
    assertEquals(1, progress.currentWave());
  }

  @Test
  void aBossGivesOneTrophyTheFirstTimeOnly() {
    Progress progress = atWave(10);
    WaveOutcome first = progression.apply(progress, report(10, true, 0, true, clean(50)), 0);
    assertEquals(1, first.trophies());
    progress.currentWave = 10;
    WaveOutcome second = progression.apply(progress, report(10, true, 0, true, clean(50)), 0);
    assertEquals(0, second.trophies());
    assertEquals(1, progress.trophies());
  }

  @Test
  void theLastWaveRepeats() {
    int last = data.balance().lastWave();
    Progress progress = atWave(last);
    progression.apply(progress, report(last, true, 0), 0);
    assertEquals(last, progress.currentWave());
  }

  @Test
  void reportMustBeForTheCurrentWave() {
    assertThrows(
        IllegalArgumentException.class, () -> progression.apply(atWave(2), report(3, true, 0), 0));
  }

  @Test
  void everyChallengeReadsTheReport() {
    WaveReport.TurretReport messy =
        new WaveReport.TurretReport(
            40, 2, 3, 4, 1, 90, List.of(new WaveReport.FaultCount(FaultType.NO_AMMO, 1, 1)));
    WaveReport won = report(1, true, 0, false, messy);
    WaveReport cleanWin = report(1, true, 0, false, clean(40));
    for (Challenge.Type type : Challenge.Type.values()) {
      Challenge challenge = new Challenge(type, type.hasLimit() ? 39 : 0);
      assertFalse(ChallengeJudge.met(challenge, won, 40), type + " fails on a messy wave");
    }
    assertTrue(ChallengeJudge.met(new Challenge(Challenge.Type.MAX_SHOTS, 40), cleanWin, 0));
    assertTrue(ChallengeJudge.met(new Challenge(Challenge.Type.MAX_MEMORY, 4), cleanWin, 4));
    assertFalse(ChallengeJudge.met(new Challenge(Challenge.Type.MAX_MEMORY, 4), cleanWin, 5));
    assertTrue(ChallengeJudge.met(new Challenge(Challenge.Type.NO_FAULTS, 0), cleanWin, 0));
    assertFalse(
        ChallengeJudge.met(
            new Challenge(Challenge.Type.NO_FAULTS, 0), report(1, false, 20, false, clean(0)), 0),
        "only a won wave");
  }

  @Test
  void syntaxIsBoughtInOrderWithStars() {
    Progress progress = atWave(1);
    progress.stars = 100;
    assertEquals(PurchaseCheck.MISSING_REQUIREMENT, shop.buy(progress, Feature.VARIABLES));
    assertEquals(PurchaseCheck.OK, shop.buy(progress, Feature.IF_ELSE));
    assertEquals(PurchaseCheck.ALREADY_OWNED, shop.buy(progress, Feature.IF_ELSE));
    assertEquals(PurchaseCheck.OK, shop.buy(progress, Feature.VARIABLES));
    assertEquals(PurchaseCheck.OK, shop.buy(progress, Feature.LOGIC_OPS));
    assertEquals(PurchaseCheck.OK, shop.buy(progress, Feature.WHILE));
    int spent = 0;
    for (Feature f :
        List.of(Feature.IF_ELSE, Feature.VARIABLES, Feature.LOGIC_OPS, Feature.WHILE)) {
      spent += data.shop().syntax(f).price();
    }
    assertEquals(100 - spent, progress.stars());
    assertEquals(PurchaseCheck.OK, shop.buy(progress, Feature.FOR));
    assertEquals(PurchaseCheck.NOT_IN_MVP, shop.buy(progress, Feature.METHODS));
  }

  private static Progress withAllSyntax(int wave) {
    Progress progress = atWave(wave);
    progress.features.addAll(
        List.of(Feature.IF_ELSE, Feature.VARIABLES, Feature.LOGIC_OPS, Feature.WHILE));
    return progress;
  }

  @Test
  void hardwareHasAPricePerLevelAndALastLevel() {
    Progress progress = withAllSyntax(1);
    List<Integer> prices = data.shop().hardware(HardwareKind.DAMAGE).prices();
    progress.stars = prices.get(0);
    assertEquals(PurchaseCheck.OK, shop.buy(progress, HardwareKind.DAMAGE));
    assertEquals(0, progress.stars());
    assertEquals(prices.get(1), shop.price(progress, HardwareKind.DAMAGE));
    assertEquals(PurchaseCheck.NOT_ENOUGH_STARS, shop.buy(progress, HardwareKind.DAMAGE));
    progress.stars = 1_000;
    for (int level = 1; level < prices.size(); level++) {
      assertEquals(PurchaseCheck.OK, shop.buy(progress, HardwareKind.DAMAGE));
    }
    assertEquals(PurchaseCheck.MAX_LEVEL, shop.check(progress, HardwareKind.DAMAGE));
    assertEquals(PurchaseCheck.NEEDS_SYNTAX, shop.check(progress, HardwareKind.MEMORY));
    progress.features.add(Feature.FOR);
    assertEquals(PurchaseCheck.OK, shop.check(progress, HardwareKind.MEMORY));
  }

  @Test
  void hardwareCanBeGivenBackForWhatItCostLevelByLevel() {
    Progress progress = withAllSyntax(1);
    List<Integer> prices = data.shop().hardware(HardwareKind.HEAT_SINK).prices();
    progress.stars = prices.get(0) + prices.get(1);
    assertFalse(shop.canRefund(progress, HardwareKind.HEAT_SINK), "nothing bought yet");
    assertFalse(shop.refund(progress, HardwareKind.HEAT_SINK));
    shop.buy(progress, HardwareKind.HEAT_SINK);
    shop.buy(progress, HardwareKind.HEAT_SINK);
    assertEquals(0, progress.stars());

    assertEquals(prices.get(1), shop.refundValue(progress, HardwareKind.HEAT_SINK));
    assertTrue(shop.refund(progress, HardwareKind.HEAT_SINK));
    assertEquals(1, progress.level(HardwareKind.HEAT_SINK));
    assertEquals(prices.get(1), progress.stars());
    assertTrue(shop.refund(progress, HardwareKind.HEAT_SINK));
    assertEquals(0, progress.level(HardwareKind.HEAT_SINK));
    assertEquals(prices.get(0) + prices.get(1), progress.stars(), "everything back");
  }

  @Test
  void theExtraTurretIsNotGivenBack() {
    Progress progress = withAllSyntax(1);
    progress.stars = 1_000;
    shop.buy(progress, HardwareKind.EXTRA_TURRET);
    assertFalse(shop.canRefund(progress, HardwareKind.EXTRA_TURRET));
    assertFalse(shop.refund(progress, HardwareKind.EXTRA_TURRET));
    assertEquals(2, progress.turretCount());
  }

  @Test
  void aThirdAndFourthTurretComeWithFor() {
    Progress progress = withAllSyntax(1);
    progress.stars = 1_000;
    assertEquals(PurchaseCheck.OK, shop.buy(progress, HardwareKind.EXTRA_TURRET));
    assertEquals(2, progress.turretCount());
    assertEquals(PurchaseCheck.NEEDS_SYNTAX, shop.buy(progress, HardwareKind.EXTRA_TURRET));
    progress.features.add(Feature.FOR);
    assertEquals(PurchaseCheck.OK, shop.buy(progress, HardwareKind.EXTRA_TURRET));
    assertEquals(PurchaseCheck.OK, shop.buy(progress, HardwareKind.EXTRA_TURRET));
    assertEquals(data.map().turretSlots().size(), progress.turretCount());
    assertEquals(PurchaseCheck.MAX_LEVEL, shop.buy(progress, HardwareKind.EXTRA_TURRET));
  }

  @Test
  void memoryUpgradesRaiseTheLinesEachScriptMayUse() {
    Progress progress = withAllSyntax(11);
    progress.features.add(Feature.FOR);
    progress.stars = 1_000;
    int base = data.turret().memoryLines();
    assertEquals(base, progress.memoryLines(data));
    shop.buy(progress, HardwareKind.MEMORY);
    assertEquals(
        base + (int) data.shop().hardware(HardwareKind.MEMORY).effectPerLevel(),
        progress.memoryLines(data));
  }

  @Test
  void hardwareCapsWaitForTheSyntaxThatLiftsThem() {
    Progress progress = atWave(1);
    progress.stars = 1_000;
    // shop.json: damage needs if/else for level 1 and variables for level 2.
    assertEquals(PurchaseCheck.NEEDS_SYNTAX, shop.check(progress, HardwareKind.DAMAGE));
    assertEquals(
        Feature.IF_ELSE, shop.unlockingFeature(progress, HardwareKind.DAMAGE).orElseThrow());
    progress.features.add(Feature.IF_ELSE);
    assertEquals(PurchaseCheck.OK, shop.buy(progress, HardwareKind.DAMAGE));
    assertEquals(PurchaseCheck.NEEDS_SYNTAX, shop.check(progress, HardwareKind.DAMAGE));
    assertEquals(
        Feature.VARIABLES, shop.unlockingFeature(progress, HardwareKind.DAMAGE).orElseThrow());
    progress.features.add(Feature.VARIABLES);
    assertEquals(PurchaseCheck.OK, shop.buy(progress, HardwareKind.DAMAGE));
    assertTrue(shop.unlockingFeature(progress, HardwareKind.DAMAGE).isEmpty());
  }

  @Test
  void theSecondTurretWaitsForLogicOperators() {
    Progress progress = atWave(1);
    progress.stars = 1_000;
    progress.features.addAll(List.of(Feature.IF_ELSE, Feature.VARIABLES));
    assertEquals(PurchaseCheck.NEEDS_SYNTAX, shop.check(progress, HardwareKind.EXTRA_TURRET));
    progress.features.add(Feature.LOGIC_OPS);
    assertEquals(PurchaseCheck.OK, shop.check(progress, HardwareKind.EXTRA_TURRET));
  }

  @Test
  void withoutCapsEverythingIsBuyable() {
    Shop free = new Shop(TestData.load().withoutHardwareCaps());
    Progress progress = atWave(1);
    progress.stars = 1_000;
    for (HardwareKind kind :
        new HardwareKind[] {
          HardwareKind.DAMAGE,
          HardwareKind.MAGAZINE,
          HardwareKind.HEAT_SINK,
          HardwareKind.EXTRA_TURRET
        }) {
      assertEquals(PurchaseCheck.OK, free.check(progress, kind), kind.name());
    }
  }

  @Test
  void ammoSwitchUnlocksWhenWaveSixIsReached() {
    Progress progress = atWave(5);
    progression.apply(progress, report(5, true, 0), 0);
    assertEquals(6, progress.unlocks().highestWaveReached());
  }
}
