package io.github.yeray06.wavedefense.script;

import static io.github.yeray06.wavedefense.script.Scripts.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.yeray06.wavedefense.script.diag.CycleOutcome;
import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import org.junit.jupiter.api.Test;

/** Every game exception, with the line it is reported at. Nothing ever escapes as a throw. */
class FaultTest {

  private static void assertFault(
      CompiledScript script, FakeTurret turret, FaultType type, int line) {
    assertEquals(CycleOutcome.FAULT, script.run(turret));
    assertEquals(type, script.faultType());
    assertEquals(line, script.faultLine());
  }

  @Test
  void firingWithAnEmptyMagazineIsNoAmmo() {
    FakeTurret turret = new FakeTurret();
    turret.ammo = 0;
    assertFault(ok("int a = 1;\ndisparar();"), turret, FaultType.NO_AMMO, 2);
  }

  @Test
  void firingAtFullHeatIsOverheat() {
    FakeTurret turret = new FakeTurret();
    // Five shots take heat from 0 to 100; the sixth one, on line 3, overheats.
    assertFault(
        ok("int n = 0;\nwhile (n < 6) {\n  disparar();\n  n++;\n}"), turret, FaultType.OVERHEAT, 3);
    assertEquals(5, turret.shotsFired);
  }

  @Test
  void theFaultStopsTheRestOfTheScript() {
    FakeTurret turret = new FakeTurret();
    turret.ammo = 0;
    assertFault(ok("disparar();\nrecargar();"), turret, FaultType.NO_AMMO, 1);
    assertEquals(0, turret.reloadCalls);
  }

  @Test
  void usingANullEnemyIsNullPointer() {
    FakeTurret turret = new FakeTurret();
    assertFault(
        ok("Enemigo e = enemigoMasCercano();\nint v = e.vida();"),
        turret,
        FaultType.NULL_POINTER,
        2);
    assertFault(ok("Enemigo e = null;\n\napuntar(e);"), turret, FaultType.NULL_POINTER, 3);
    assertFault(ok("boolean b = objetivo().blindado();"), turret, FaultType.NULL_POINTER, 1);
  }

  @Test
  void usingADeadEnemyIsNullPointer() {
    FakeTurret turret = new FakeTurret();
    long enemy = turret.addEnemy(2, 10, 100, false, 500);
    turret.nearest = enemy;
    turret.kill(enemy);
    assertFault(
        ok("Enemigo e = enemigoMasCercano();\nif (e.distanciaAlNucleo() < 900) disparar();"),
        turret,
        FaultType.NULL_POINTER,
        2);
    assertFault(ok("int s = enemigoMasCercano().velocidad();"), turret, FaultType.NULL_POINTER, 1);
  }

  @Test
  void aWhileMayRunExactlyOneThousandIterations() {
    FakeTurret turret = new FakeTurret();
    CompiledScript script = ok("int n = 0;\nwhile (n < 1000) {\n  n++;\n}");
    assertEquals(CycleOutcome.COMPLETED, script.run(turret));
  }

  @Test
  void iterationOneThousandAndOneIsAnInfiniteLoopAtTheWhile() {
    FakeTurret turret = new FakeTurret();
    assertFault(
        ok("int n = 0;\nwhile (n < 1001) {\n  n++;\n}"), turret, FaultType.INFINITE_LOOP, 2);
    assertFault(ok("disparar();\nwhile (true) {\n}"), turret, FaultType.INFINITE_LOOP, 2);
    assertFault(ok("while (true);"), turret, FaultType.INFINITE_LOOP, 1);
  }

  @Test
  void theIterationLimitIsPerEntryNotPerCycle() {
    // The inner loop runs 600 times on each of 3 entries: 1,800 in the cycle, never 1,001 at once.
    String source =
        """
        int i = 0;
        while (i < 3) {
          int j = 0;
          while (j < 600) {
            j++;
          }
          i++;
        }
        disparar();
        """;
    FakeTurret turret = new FakeTurret();
    assertEquals(CycleOutcome.COMPLETED, ok(source).run(turret));
    assertEquals(1, turret.shotsFired);
  }

  @Test
  void nestedLoopsHitTheStepBudgetAndBlameTheInnermostLoop() {
    // 999 x 999 iterations stay under the per-loop limit but blow the cycle budget.
    String source =
        """
        int i = 0;
        while (i < 999) {
          int j = 0;
          while (j < 999) {
            j++;
          }
          i++;
        }
        """;
    assertFault(ok(source), new FakeTurret(), FaultType.INFINITE_LOOP, 4);
  }

  @Test
  void divisionAndRemainderByZeroAreArithmetic() {
    FakeTurret turret = new FakeTurret();
    assertFault(ok("int z = 0;\nint x = 5 / z;"), turret, FaultType.ARITHMETIC, 2);
    assertFault(ok("int z = 0;\n\nint x = 5 % z;"), turret, FaultType.ARITHMETIC, 3);
    assertFault(ok("int x = 5;\nx /= 0;"), turret, FaultType.ARITHMETIC, 2);
    assertFault(ok("int x = 5;\nx %= 0;"), turret, FaultType.ARITHMETIC, 2);
  }

  @Test
  void aTurretBugBecomesAnInternalErrorWithTheLine() {
    FakeTurret turret = new FakeTurret();
    IllegalStateException bug = new IllegalStateException("simulation bug");
    turret.failure = bug;
    CompiledScript script = ok("int a = 1;\ndisparar();");
    assertFault(script, turret, FaultType.INTERNAL_ERROR, 2);
    assertSame(bug, script.internalError());
  }

  @Test
  void faultStateIsClearedOnTheNextRun() {
    FakeTurret turret = new FakeTurret();
    turret.ammo = 0;
    CompiledScript script = ok("if (municion() > 0) disparar(); else disparar();");
    script.run(turret);
    assertNotNull(script.faultType());
    turret.ammo = 5;
    assertEquals(CycleOutcome.COMPLETED, script.run(turret));
    assertNull(script.faultType());
    assertEquals(0, script.faultLine());
  }
}
