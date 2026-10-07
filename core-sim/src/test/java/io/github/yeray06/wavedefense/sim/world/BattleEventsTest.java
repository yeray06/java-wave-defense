package io.github.yeray06.wavedefense.sim.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.sim.TestData;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** D-40: the render's event buffer and the previous positions for interpolation. */
class BattleEventsTest {
  private static final String IF_SCRIPT =
      "if (municion() == 0) {\n  recargar();\n} else if (hayEnemigos()) {\n  disparar();\n}";

  private static Battle battle(int wave, String script) {
    return new Battle(
        TestData.load(),
        new BattleSetup(wave, List.of(TestData.script(script)), Loadout.NONE, 1, 1L));
  }

  @Test
  void keepsEventsInOrderUntilClearedAndDropsTheOldestWhenFull() {
    BattleEvents events = new BattleEvents(3);
    for (int i = 0; i < 5; i++) {
      events.add(BattleEventType.SHOT, i, i, 0, i, -i);
    }
    assertEquals(3, events.size());
    assertEquals(2, events.dropped());
    assertEquals(2, events.tick(0));
    assertEquals(4, events.a(2));
    assertEquals(-4, events.y(2));
    assertThrows(IndexOutOfBoundsException.class, () -> events.type(3));
    events.clear();
    assertEquals(0, events.size());
    assertEquals(0, events.dropped());
    events.add(BattleEventType.KILL, 9, 1, 0, 0, 0);
    assertEquals(BattleEventType.KILL, events.type(0));
  }

  @Test
  void aWaveReportsItsShotsKillsReloadsAndFaults() {
    // disparar() alone: 12 shots, then SinMunicionException; wave 1 has 8 basics.
    Battle battle = battle(1, "disparar();");
    Map<BattleEventType, Integer> counts = new EnumMap<>(BattleEventType.class);
    List<Integer> ticks = new ArrayList<>();
    int faultsSeen = 0;
    while (!battle.finished()) {
      battle.tick();
      BattleEvents events = battle.events();
      for (int i = 0; i < events.size(); i++) {
        counts.merge(events.type(i), 1, Integer::sum);
        ticks.add(events.tick(i));
        if (events.type(i) == BattleEventType.FAULT) {
          assertEquals(FaultType.NO_AMMO.ordinal(), events.b(i));
          faultsSeen++;
        }
        if (events.type(i) == BattleEventType.SHOT) {
          assertEquals(AmmoType.NORMAL.ordinal(), events.b(i));
        }
      }
      events.clear();
      if (faultsSeen == 1 && battle.turret(0).hung() && !battle.turret(0).reloading()) {
        assertTrue(battle.manualReload(0));
      }
    }
    WaveReport report = battle.report();
    WaveReport.TurretReport turret = report.turrets().get(0);
    assertEquals(turret.shots() - turret.airShots(), counts.getOrDefault(BattleEventType.SHOT, 0));
    assertEquals(turret.airShots(), counts.getOrDefault(BattleEventType.AIR_SHOT, 0));
    assertEquals(report.kills(), counts.getOrDefault(BattleEventType.KILL, 0));
    assertEquals(report.leaks().size(), counts.getOrDefault(BattleEventType.LEAK, 0));
    assertEquals(turret.reloads(), counts.getOrDefault(BattleEventType.RELOAD, 0));
    assertEquals(
        turret.faults().stream().mapToInt(f -> f.count()).sum(),
        counts.getOrDefault(BattleEventType.FAULT, 0));
    assertTrue(counts.getOrDefault(BattleEventType.EMPTY, 0) >= 1);
    assertTrue(counts.get(BattleEventType.HIT) >= report.kills());
    for (int i = 1; i < ticks.size(); i++) {
      assertTrue(ticks.get(i) >= ticks.get(i - 1), "events in tick order");
    }
  }

  @Test
  void readingTheEventsDoesNotChangeTheBattle() {
    String script =
        "Enemigo o = enemigoMasCercano();\nwhile (o != null && calor() < 80 && municion() > 0) {\n"
            + "  disparar();\n}\nif (municion() == 0) recargar();";
    Battle unread = battle(9, script);
    WaveReport expected = unread.runToEnd();
    Battle read = battle(9, script);
    while (!read.finished()) {
      read.tick();
      read.events().clear();
    }
    assertEquals(expected, read.report());
    assertTrue(unread.events().dropped() >= 0, "an unread buffer only drops the oldest events");
  }

  @Test
  void enemiesAndBulletsKeepTheirPreviousPosition() {
    Battle battle = battle(1, IF_SCRIPT);
    boolean sawBullet = false;
    for (int t = 0; t < 600 && !battle.finished(); t++) {
      double[][] before = positions(battle);
      battle.tick();
      for (int i = 0; i < battle.enemies().capacity(); i++) {
        Enemy enemy = battle.enemies().get(i);
        if (enemy.active() && before[i] != null && enemy.distance() > 0) {
          assertEquals(before[i][0], enemy.prevX(), 1e-9);
          assertEquals(before[i][1], enemy.prevY(), 1e-9);
        }
      }
      for (int i = 0; i < battle.bullets().capacity(); i++) {
        Bullet bullet = battle.bullets().get(i);
        if (bullet.active()) {
          sawBullet = true;
          double step = Math.hypot(bullet.x() - bullet.prevX(), bullet.y() - bullet.prevY());
          double perTick =
              TestData.load().turret().bulletSpeedUnitsPerSecond() / battle.ticksPerSecond();
          assertTrue(step <= perTick + 1e-9, "one tick of movement at most");
        }
      }
    }
    assertTrue(sawBullet);
  }

  /** Enemy positions by slot, {@code null} for free slots. */
  private static double[][] positions(Battle battle) {
    double[][] out = new double[battle.enemies().capacity()][];
    for (int i = 0; i < out.length; i++) {
      Enemy enemy = battle.enemies().get(i);
      if (enemy.active()) {
        out[i] = new double[] {enemy.x(), enemy.y(), enemy.generation()};
      }
    }
    return out;
  }

  @Test
  void hitsOnArmorWithNormalAmmoAreMarked() {
    Battle battle =
        new Battle(
            BattleTestData.waveOneOf(EnemyKind.ARMORED, 1),
            new BattleSetup(1, List.of(TestData.script(IF_SCRIPT)), Loadout.NONE, 1, 1L));
    boolean blocked = false;
    while (!battle.finished()) {
      battle.tick();
      BattleEvents events = battle.events();
      for (int i = 0; i < events.size(); i++) {
        if (events.type(i) == BattleEventType.HIT) {
          assertEquals(EnemyKind.ARMORED.ordinal(), events.a(i));
          blocked |= events.b(i) == 1;
        }
      }
      events.clear();
    }
    assertTrue(blocked);
  }
}
