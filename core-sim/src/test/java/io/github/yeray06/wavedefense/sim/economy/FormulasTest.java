package io.github.yeray06.wavedefense.sim.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.yeray06.wavedefense.sim.TestData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The plan's table in "Economía y farmeo", reproduced from the shipped data. Its money columns went
 * with the money (D-57); enemies and health still follow the plan.
 */
class FormulasTest {
  private final Formulas formulas = new Formulas(TestData.load().balance());

  @ParameterizedTest(name = "oleada {0}")
  @CsvSource({
    // oleada, enemigos, vida por enemigo (as printed)
    "1, 8, 10",
    "5, 16, 19",
    "10, 26, 44",
    "20, 46, 232",
    "30, 60, 1215"
  })
  void matchesThePlansTable(int wave, int count, int health) {
    assertEquals(count, formulas.enemyCount(wave));
    assertEquals(health, Math.round(formulas.enemyHealth(wave)));
  }
}
