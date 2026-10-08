package io.github.yeray06.wavedefense.script;

import static io.github.yeray06.wavedefense.script.Scripts.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.yeray06.wavedefense.script.api.EnemyHandle;
import io.github.yeray06.wavedefense.script.diag.CycleOutcome;
import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import org.junit.jupiter.api.Test;

/** Both forms of {@code for}, the enemy list and their exceptions, run on a fake turret. */
class ForLoopTest {

  private static FakeTurret turretWithEnemies(int count) {
    FakeTurret turret = new FakeTurret();
    for (int slot = 1; slot <= count; slot++) {
      turret.addEnemy(slot, 10 * slot, 100, false, 1000 - 100 * slot);
    }
    return turret;
  }

  private static void runs(CompiledScript script, FakeTurret turret) {
    assertEquals(
        CycleOutcome.COMPLETED,
        script.run(turret),
        () -> script.faultType() + " at " + script.faultLine());
  }

  private static void faults(CompiledScript script, FakeTurret turret, FaultType type, int line) {
    assertEquals(CycleOutcome.FAULT, script.run(turret));
    assertEquals(type, script.faultType());
    assertEquals(line, script.faultLine());
  }

  @Test
  void aCountingForRunsItsBodyAsManyTimesAsJava() {
    FakeTurret turret = new FakeTurret();
    runs(ok("for (int i = 0; i < 3; i++) {\n  disparar();\n}"), turret);
    assertEquals(3, turret.shotsFired);

    turret = new FakeTurret();
    runs(ok("for (int i = 10; i > 0; i -= 4) disparar();"), turret);
    assertEquals(3, turret.shotsFired, "10, 6 and 2");

    turret = new FakeTurret();
    runs(ok("int n = 0;\nfor (n = 5; n < 3; n++) disparar();\nif (n == 5) disparar();"), turret);
    assertEquals(1, turret.shotsFired, "the body never runs, the init does");
  }

  @Test
  void initAndUpdateCanHoldSeveralStatements() {
    FakeTurret turret = new FakeTurret();
    runs(
        ok("int a;\nint b;\n" + "for (a = 0, b = 10; a < b; a++, b--) {\n  disparar();\n}"),
        turret);
    assertEquals(5, turret.shotsFired, "Java: (0,10) (1,9) (2,8) (3,7) (4,6)");
  }

  @Test
  void theCountingVariableLivesOnlyInsideTheLoop() {
    FakeTurret turret = new FakeTurret();
    runs(
        ok("for (int i = 0; i < 2; i++) disparar();\nfor (int i = 0; i < 2; i++) disparar();"),
        turret);
    assertEquals(4, turret.shotsFired);
  }

  @Test
  void aForWithoutConditionStopsAtTheLoopLimit() {
    faults(ok("for (;;) {\n  municion();\n}"), new FakeTurret(), FaultType.INFINITE_LOOP, 1);
  }

  @Test
  void forEachVisitsEveryEnemyInRangeInOrder() {
    FakeTurret turret = turretWithEnemies(3);
    runs(
        ok(
            """
            Enemigo debil = null;
            for (Enemigo e : enemigos()) {
              if (debil == null || e.vida() < debil.vida()) {
                debil = e;
              }
            }
            apuntar(debil);
            """),
        turret);
    assertEquals(1, turret.lastAimed, "slot 1 has 10 health, the least");

    turret = turretWithEnemies(4);
    turret.kill(2);
    runs(ok("for (Enemigo e : enemigos()) disparar();"), turret);
    assertEquals(3, turret.shotsFired, "a dead enemy is not in the list");
  }

  @Test
  void forEachOverAnEmptyListDoesNothing() {
    FakeTurret turret = new FakeTurret();
    runs(ok("for (Enemigo e : enemigos()) disparar();"), turret);
    assertEquals(0, turret.shotsFired);
  }

  @Test
  void sizeGetAndIsEmptyReadTheList() {
    FakeTurret turret = turretWithEnemies(3);
    runs(
        ok(
            """
            List<Enemigo> lista = enemigos();
            if (!lista.isEmpty() && lista.size() == 3) {
              apuntar(lista.get(lista.size() - 1));
            }
            """),
        turret);
    assertEquals(3, turret.lastAimed);

    turret = turretWithEnemies(2);
    runs(
        ok("for (int i = 0; i < enemigos().size(); i++) {\n  apuntar(enemigos().get(i));\n}"),
        turret);
    assertEquals(2, turret.lastAimed);

    turret = new FakeTurret();
    runs(ok("if (enemigos().isEmpty()) disparar();"), turret);
    assertEquals(1, turret.shotsFired);
  }

  @Test
  void getOutsideTheListIsIndexOutOfBounds() {
    faults(
        ok("int n = 0;\nEnemigo e = enemigos().get(n);"),
        new FakeTurret(),
        FaultType.INDEX_OUT_OF_BOUNDS,
        2);
    faults(
        ok("List<Enemigo> l = enemigos();\napuntar(l.get(l.size()));"),
        turretWithEnemies(2),
        FaultType.INDEX_OUT_OF_BOUNDS,
        2);
    faults(
        ok("Enemigo e = enemigos().get(-1);"),
        turretWithEnemies(2),
        FaultType.INDEX_OUT_OF_BOUNDS,
        1);
  }

  @Test
  void aNullListIsANullPointer() {
    faults(
        ok("List<Enemigo> l = null;\nint n = l.size();"),
        new FakeTurret(),
        FaultType.NULL_POINTER,
        2);
    faults(
        ok("List<Enemigo> l = null;\nfor (Enemigo e : l) {\n  disparar();\n}"),
        new FakeTurret(),
        FaultType.NULL_POINTER,
        2);
  }

  @Test
  void listsCompareByReferenceLikeJava() {
    FakeTurret turret = new FakeTurret();
    runs(
        ok(
            "List<Enemigo> l = null;\nif (l == null) disparar();\nif (enemigos() != null) disparar();"),
        turret);
    assertEquals(2, turret.shotsFired);
    assertEquals(EnemyHandle.NONE, turret.lastAimed);
  }
}
