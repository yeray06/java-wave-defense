package io.github.yeray06.wavedefense.sim.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import io.github.yeray06.wavedefense.sim.TestData;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.WaveTable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The turret and enemy rules of the plan and of the phase 0 decisions, one by one. */
class BattleTest {
  private static final String IF_SCRIPT =
      "if (municion() == 0) {\n  recargar();\n} else if (hayEnemigos()) {\n  disparar();\n}";
  private static final int TICKS_PER_CYCLE = 30;

  private static GameData data() {
    return TestData.load();
  }

  /** The shipped data, but wave 1 holds only {@code groups}. */
  private static GameData waveOneOf(WaveTable.Group... groups) {
    GameData d = data();
    WaveTable waves =
        new WaveTable(
            List.of(),
            d.waves().spawnWindowSeconds(),
            List.of(new WaveTable.WaveSpec(1, List.of(groups))));
    return new GameData(
        d.balance(), d.turret(), d.enemies(), waves, d.shop(), d.map(), d.scriptApi());
  }

  private static Battle battle(GameData data, int wave, String... scripts) {
    List<CompiledScript> compiled = new ArrayList<>();
    for (String source : scripts) {
      compiled.add(source == null ? null : TestData.script(source));
    }
    return new Battle(data, new BattleSetup(wave, compiled, Loadout.NONE, 1));
  }

  private static void ticks(Battle battle, int count) {
    for (int i = 0; i < count && !battle.finished(); i++) {
      battle.tick();
    }
  }

  @Test
  void sameSetupGivesTheSameReport() {
    String burst =
        "Enemigo o = enemigoMasCercano();\nwhile (o != null && calor() < 80 && municion() > 0) {\n"
            + "  disparar();\n}\nif (municion() == 0) recargar();";
    WaveReport first = battle(data(), 10, burst, IF_SCRIPT).runToEnd();
    WaveReport second = battle(data(), 10, burst, IF_SCRIPT).runToEnd();
    assertEquals(first, second);
  }

  @Test
  void waveOneIsWonPerfectlyWithTheIfScript() {
    WaveReport report = battle(data(), 1, IF_SCRIPT).runToEnd();
    assertTrue(report.won());
    assertTrue(report.perfect());
    assertEquals(8, report.kills());
    assertEquals(8 * 200, report.earnedCents());
  }

  @Test
  void firingWithAnEmptyMagazineThrowsAndHangsForThreeSeconds() {
    Battle battle = battle(data(), 1, "disparar();");
    // 12 shots empty the magazine; the 13th cycle throws SinMunicionException.
    ticks(battle, 12 * TICKS_PER_CYCLE + 1);
    Turret turret = battle.turret(0);
    assertTrue(turret.hung());
    assertEquals(FaultType.NO_AMMO, turret.lastFault());
    assertEquals(1, turret.lastFaultLine());
    ticks(battle, 3 * 60 - 1);
    assertTrue(turret.hung(), "still hung one tick before three seconds");
    assertEquals(1, battle.report().turrets().get(0).faultCount());
    // Three seconds are exactly six cycles: it wakes up on a cycle, runs, and throws again.
    battle.tick();
    WaveReport.TurretReport report = battle.report().turrets().get(0);
    assertEquals(2, report.faultCount());
    assertEquals(3 * 60, report.hungTicks());
  }

  @Test
  void reloadKeepsTheTurretBusyThreeCyclesThenFillsTheMagazine() {
    Battle battle = battle(data(), 1, "if (municion() == 0) recargar(); else disparar();");
    ticks(battle, 12 * TICKS_PER_CYCLE + 1); // cycle 13 calls recargar()
    Turret turret = battle.turret(0);
    assertTrue(turret.reloading());
    assertEquals(0, turret.ammo());
    ticks(battle, 2 * TICKS_PER_CYCLE);
    assertTrue(turret.reloading(), "still busy after two more cycles");
    ticks(battle, TICKS_PER_CYCLE);
    assertFalse(turret.reloading());
    assertEquals(12, turret.ammo());
  }

  @Test
  void heatDissipatesFirstAndOverheatsOnlyAtOneHundred() {
    // Five shots go 0 → 100; the sixth, on line 6, finds heat at 100 and throws.
    Battle battle =
        battle(
            data(),
            1,
            "disparar();\ndisparar();\ndisparar();\ndisparar();\ndisparar();\ndisparar();");
    battle.tick();
    Turret turret = battle.turret(0);
    assertEquals(FaultType.OVERHEAT, turret.lastFault());
    assertEquals(6, turret.lastFaultLine());
    assertEquals(100, turret.heat());
    // While hung, heat still dissipates 30 per cycle.
    ticks(battle, TICKS_PER_CYCLE);
    assertEquals(70, turret.heat());
  }

  @Test
  void oneShotPerCycleIsSustainable() {
    Battle battle = battle(data(), 1, IF_SCRIPT);
    for (int i = 0; i < 40 * TICKS_PER_CYCLE && !battle.finished(); i++) {
      battle.tick();
      assertTrue(battle.turret(0).heat() <= 20);
    }
    assertEquals(0, battle.report().turrets().get(0).faultCount());
  }

  @Test
  void shotsWithNoEnemyInRangeGoIntoTheAir() {
    Battle battle = battle(data(), 1, "disparar();");
    battle.tick();
    assertEquals(1, battle.report().turrets().get(0).airShots());
  }

  @Test
  void armorTakesEightyPercentLessFromNormalAmmoButNotFromPiercing() {
    GameData armoredOnly = waveOneOf(new WaveTable.Group(EnemyKind.ARMORED, 1));
    WaveReport normal = battle(armoredOnly, 1, IF_SCRIPT).runToEnd();
    WaveReport piercing =
        battle(
                armoredOnly,
                1,
                "usarMunicion(PERFORANTE);\nif (municion() == 0) recargar(); else if (hayEnemigos()) disparar();")
            .runToEnd();
    assertEquals(1, piercing.kills());
    // 10 health: 2 piercing shots of 5, or 10 normal shots of 1.
    assertEquals(2, piercing.turrets().get(0).shots() - piercing.turrets().get(0).airShots());
    assertEquals(
        10,
        normal.turrets().get(0).shots()
            - normal.turrets().get(0).airShots()
            - normal.turrets().get(0).wastedOnDead());
  }

  @Test
  void regeneratorHealsAfterOneSecondWithoutDamage() {
    GameData regenOnly = waveOneOf(new WaveTable.Group(EnemyKind.REGENERATOR, 1));
    // Fires exactly once, when the enemy first comes into range.
    Battle battle = battle(regenOnly, 1, "if (hayEnemigos() && municion() == 12) disparar();");
    Enemy enemy = battle.enemies().get(0);
    while (enemy.health() == enemy.maxHealth()) {
      battle.tick();
    }
    double damaged = enemy.health();
    ticks(battle, 59);
    assertEquals(damaged, enemy.health(), "no healing before one second");
    ticks(battle, 60);
    assertTrue(enemy.health() > damaged);
    ticks(battle, 4 * 60);
    assertEquals(enemy.maxHealth(), enemy.health());
  }

  @Test
  void bossAlternatesArmorEveryFiveSecondsAndLosesTheWaveIfItLeaks() {
    GameData bossOnly = waveOneOf(new WaveTable.Group(EnemyKind.BOSS, 1));
    Battle battle = battle(bossOnly, 1, (String) null);
    battle.tick();
    Enemy boss = battle.enemies().get(0);
    boolean start = boss.armored();
    ticks(battle, 5 * 60);
    assertEquals(!start, boss.armored());
    ticks(battle, 5 * 60);
    assertEquals(start, boss.armored());
    assertEquals(20 * 10.0, boss.maxHealth(), 1e-9);

    WaveReport report = battle.runToEnd();
    assertFalse(report.won());
    assertTrue(report.bossLeaked());
    assertEquals(1, report.leaks().size());
  }

  @Test
  void twentyLeaksLoseTheWaveAndKeepTheKillsPaid() {
    Battle battle = battle(data(), 10, (String) null);
    WaveReport report = battle.runToEnd();
    assertFalse(report.won());
    assertEquals(20, report.leaks().size());
    assertEquals(0, battle.coreLives());
    assertTrue(report.ticks() < 60 * 60);
  }

  @Test
  void swarmMembersSpawnTogether() {
    GameData swarm = waveOneOf(new WaveTable.Group(EnemyKind.SWARM, 10));
    Battle battle = battle(swarm, 1, (String) null);
    ticks(battle, 60);
    assertEquals(10, battle.enemies().activeCount());
    double[] distances = new double[10];
    for (int i = 0; i < 10; i++) {
      distances[i] = battle.enemies().get(i).distance();
    }
    Arrays.sort(distances);
    assertTrue(distances[9] - distances[0] < 12 * 10, "members within a group's length");
  }

  @Test
  void manualReloadWorksWhileHungAndIsRefusedWhileReloading() {
    Battle battle = battle(data(), 1, "disparar();");
    ticks(battle, 12 * TICKS_PER_CYCLE + 1);
    Turret turret = battle.turret(0);
    assertTrue(turret.hung());
    assertTrue(battle.manualReload(0), "the emergency click works on a hung turret (D-03b)");
    assertFalse(battle.manualReload(0), "already reloading");
    ticks(battle, 3 * TICKS_PER_CYCLE);
    assertEquals(12, turret.ammo(), "the reload finished during the hang");
    // Once the hang ends the script fires again instead of throwing.
    ticks(battle, 3 * 60);
    assertEquals(1, battle.report().turrets().get(0).faultCount());
  }

  @Test
  void aimedTargetIsKeptAndObjetivoIsNullOnceItDies() {
    String lock =
        "Enemigo o = objetivo();\nif (o == null) {\n  o = enemigoMasCercano();\n"
            + "  if (o != null) apuntar(o);\n}\nif (o != null && municion() > 0) disparar();\n"
            + "if (municion() == 0) recargar();";
    WaveReport report = battle(data(), 4, lock).runToEnd();
    assertEquals(0, report.turrets().get(0).faultCount());
    assertTrue(report.kills() > 0);
  }
}
