package io.github.yeray06.wavedefense.sim.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.sim.TestData;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.world.WaveReport;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Plan, "Farmeo" and "Derrota y reinicio", plus D-02 and D-10. */
class ProgressionTest {
  private final Progression progression = new Progression(TestData.load());
  private final Shop shop = new Shop(TestData.load());

  private static WaveReport report(int wave, boolean won, int leaks, long cents, boolean boss) {
    List<WaveReport.Leak> leakList =
        java.util.Collections.nCopies(leaks, new WaveReport.Leak(EnemyKind.BASIC, 1, -1));
    return new WaveReport(wave, won, 10, 10 - leaks, leakList, boss, false, cents, 2700, List.of());
  }

  private static Progress atWave(int wave) {
    Progress progress = Progress.newGame(TestData.load());
    progress.currentWave = wave;
    progress.highestWaveReached = wave;
    return progress;
  }

  @Test
  void newGameStartsAtWaveOneWithOneTurretNothingBoughtAndAutoAdvanceOn() {
    Progress progress = Progress.newGame(TestData.load());
    assertEquals(1, progress.currentWave());
    assertEquals(1, progress.turretCount());
    assertEquals(0, progress.moneyCents());
    assertTrue(progress.autoAdvance());
    assertTrue(progress.features().isEmpty());
  }

  @Test
  void perfectWavePaysTwentyFivePercentMoreAndUnlocksSpeedUp() {
    Progress progress = atWave(1);
    WaveOutcome outcome = progression.apply(progress, report(1, true, 0, 1600, false));
    assertEquals(400, outcome.perfectBonusCents());
    assertEquals(2000, progress.moneyCents());
    assertTrue(progress.speedUpAllowed(1));
    assertEquals(2, progress.currentWave());
  }

  @Test
  void leakyWinPaysNoBonusAndDoesNotUnlockSpeedUp() {
    Progress progress = atWave(1);
    progression.apply(progress, report(1, true, 3, 1000, false));
    assertEquals(1000, progress.moneyCents());
    assertFalse(progress.speedUpAllowed(1));
    assertTrue(progress.beaten(1));
  }

  @Test
  void withAutoAdvanceOffTheSameWaveRepeats() {
    Progress progress = atWave(3);
    progress.setAutoAdvance(false);
    progression.apply(progress, report(3, true, 0, 100, false));
    assertEquals(3, progress.currentWave());
  }

  @Test
  void defeatPaysTheKillsGoesBackOneWaveAndTurnsAutoAdvanceOff() {
    Progress progress = atWave(5);
    progress.features.add(Feature.IF_ELSE);
    progress.levels.put(HardwareKind.DAMAGE, 2);
    WaveOutcome outcome = progression.apply(progress, report(5, false, 20, 700, false));
    assertEquals(700, outcome.totalCents());
    assertEquals(4, progress.currentWave());
    assertFalse(progress.autoAdvance());
    assertTrue(progress.owns(Feature.IF_ELSE), "syntax is kept");
    assertEquals(2, progress.level(HardwareKind.DAMAGE), "hardware is kept");
    assertEquals(5, progress.highestWaveReached());
  }

  @Test
  void losingWaveOneStaysOnWaveOne() {
    Progress progress = atWave(1);
    progression.apply(progress, report(1, false, 20, 0, false));
    assertEquals(1, progress.currentWave());
  }

  @Test
  void firstBossKillPaysThreeWavesAndLaterOnesTwentyEnemies() {
    Progress progress = atWave(10);
    WaveOutcome first = progression.apply(progress, report(10, true, 0, 0, true));
    assertEquals(3 * 26 * 704, first.bossBonusCents());
    WaveOutcome second = progression.apply(progress, report(10, true, 0, 0, true));
    assertEquals(20 * 704, second.bossBonusCents());
    assertEquals(10, progress.currentWave(), "the last wave repeats");
  }

  @Test
  void reportMustBeForTheCurrentWave() {
    assertThrows(
        IllegalArgumentException.class,
        () -> progression.apply(atWave(2), report(3, true, 0, 0, false)));
  }

  @Test
  void syntaxIsBoughtInOrderWithThePlansPrices() {
    Progress progress = atWave(1);
    progress.moneyCents = 100_000;
    assertEquals(PurchaseCheck.MISSING_REQUIREMENT, shop.buy(progress, Feature.VARIABLES));
    assertEquals(PurchaseCheck.OK, shop.buy(progress, Feature.IF_ELSE));
    assertEquals(PurchaseCheck.ALREADY_OWNED, shop.buy(progress, Feature.IF_ELSE));
    assertEquals(PurchaseCheck.OK, shop.buy(progress, Feature.VARIABLES));
    assertEquals(PurchaseCheck.OK, shop.buy(progress, Feature.LOGIC_OPS));
    assertEquals(PurchaseCheck.OK, shop.buy(progress, Feature.WHILE));
    assertEquals(100_000 - 2_500 - 6_000 - 10_000 - 20_000, progress.moneyCents());
    assertEquals(PurchaseCheck.NOT_IN_MVP, shop.buy(progress, Feature.FOR));
  }

  @Test
  void hardwareCostsGrowAndMoneyIsChecked() {
    Progress progress = atWave(1);
    progress.moneyCents = 5_000;
    assertEquals(PurchaseCheck.OK, shop.buy(progress, HardwareKind.DAMAGE));
    assertEquals(3_000, progress.moneyCents());
    assertEquals(PurchaseCheck.NOT_ENOUGH_MONEY, shop.buy(progress, HardwareKind.DAMAGE));
    assertEquals(3_200, shop.priceCents(progress, HardwareKind.DAMAGE));
    assertEquals(PurchaseCheck.NOT_IN_MVP, shop.check(progress, HardwareKind.MEMORY));
  }

  @Test
  void theMvpHasTwoTurrets() {
    Progress progress = atWave(1);
    progress.moneyCents = 1_000_000;
    assertEquals(PurchaseCheck.OK, shop.buy(progress, HardwareKind.EXTRA_TURRET));
    assertEquals(2, progress.turretCount());
    assertEquals(PurchaseCheck.MAX_LEVEL, shop.buy(progress, HardwareKind.EXTRA_TURRET));
  }

  @Test
  void ammoSwitchUnlocksWhenWaveSixIsReached() {
    Progress progress = atWave(5);
    progression.apply(progress, report(5, true, 0, 0, false));
    assertEquals(6, progress.unlocks().highestWaveReached());
  }
}
