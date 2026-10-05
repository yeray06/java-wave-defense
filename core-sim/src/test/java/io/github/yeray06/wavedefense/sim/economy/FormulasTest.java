package io.github.yeray06.wavedefense.sim.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.yeray06.wavedefense.sim.TestData;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The plan's table in "Economía y farmeo", reproduced from the shipped data. */
class FormulasTest {
  private final Formulas formulas = new Formulas(TestData.load().balance());

  @ParameterizedTest(name = "oleada {0}")
  @CsvSource({
    // oleada, enemigos, vida por enemigo, dinero por enemigo, dinero por oleada (as printed)
    "1, 8, 10, 2, 16",
    "5, 16, 19, 3.5, 56",
    "10, 26, 44, 7, 183",
    "20, 46, 232, 28, 1309",
    "30, 60, 1215, 115, 6909"
  })
  void matchesThePlansTable(int wave, int count, int health, double reward, long waveMoney) {
    assertEquals(count, formulas.enemyCount(wave));
    assertEquals(health, Math.round(formulas.enemyHealth(wave)));
    // The plan prints one decimal at most and drops it past 5.
    double perEnemy = Money.units(formulas.rewardPerEnemyCents(wave));
    assertEquals(reward, wave >= 10 ? Math.round(perEnemy) : Math.round(perEnemy * 10) / 10.0);
    assertEquals(waveMoney, Math.round(Money.units(formulas.waveRewardCents(wave))));
  }

  @Test
  void plansFarmingExampleWaveElevenPays227Or283() {
    // Plan, "Farmeo": "repites la 11, que paga 227, o 283 si es perfecta".
    long cents = formulas.waveRewardCents(11);
    assertEquals(227, Math.round(Money.units(cents)));
    assertEquals(283, Math.round(Money.units(cents) * 1.25));
  }

  @Test
  void hardwareCostsGrowPerLevel() {
    var damage = TestData.load().shop().hardware(HardwareKind.DAMAGE);
    assertEquals(2000, Formulas.hardwareCostCents(damage, 0));
    assertEquals(3200, Formulas.hardwareCostCents(damage, 1));
    assertEquals(5120, Formulas.hardwareCostCents(damage, 2));
    var turret = TestData.load().shop().hardware(HardwareKind.EXTRA_TURRET);
    assertEquals(7500, Formulas.hardwareCostCents(turret, 0));
    assertEquals(22500, Formulas.hardwareCostCents(turret, 1));
  }

  @Test
  void moneyIsExactInCentsAndShownRoundedDown() {
    assertEquals(230, Money.cents(2.3));
    assertEquals(265, Money.cents(2.645));
    assertEquals(3, Money.wholeUnits(399));
    assertEquals(-1, Money.wholeUnits(-1));
  }
}
