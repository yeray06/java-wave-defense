package io.github.yeray06.wavedefense.script;

import static io.github.yeray06.wavedefense.script.Scripts.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.api.EnemyHandle;
import io.github.yeray06.wavedefense.script.diag.CycleOutcome;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Every construct, run against a fake turret and checked against what real Java does. */
class InterpreterTest {

  /** Whether {@code condition} holds after {@code declarations}, run by the interpreter. */
  private static boolean holds(String declarations, String condition) {
    FakeTurret turret = new FakeTurret();
    CompiledScript script = ok(declarations + "\nif (" + condition + ") disparar();");
    assertEquals(CycleOutcome.COMPLETED, script.run(turret), () -> "fault " + script.faultType());
    return turret.shotsFired == 1;
  }

  /** Expressions paired with the value real Java computes for them. */
  static Stream<Arguments> arithmetic() {
    return Stream.of(
        arguments("7 + 3", 7 + 3),
        arguments("7 - 3", 7 - 3),
        arguments("7 * 3", 7 * 3),
        arguments("7 / 2", 7 / 2),
        arguments("-7 / 2", -7 / 2),
        arguments("7 % 3", 7 % 3),
        arguments("-7 % 3", -7 % 3),
        arguments("7 % -3", 7 % -3),
        arguments("2 + 3 * 4", 2 + 3 * 4),
        arguments("(2 + 3) * 4", (2 + 3) * 4),
        arguments("10 - 4 - 3", 10 - 4 - 3),
        arguments("100 / 10 / 5", 100 / 10 / 5),
        arguments("-(2 - 5)", -(2 - 5)),
        arguments("+4", +4),
        arguments("- -4", - -4),
        arguments("-2147483648", -2147483648),
        arguments("2147483647 + 1", add(2147483647, 1)),
        arguments("-2147483648 - 1", subtract(-2147483648, 1)),
        arguments("65536 * 65536", multiply(65536, 65536)),
        arguments("-2147483648 / -1", divide(-2147483648, -1)));
  }

  // Through methods so javac computes the overflow at run time, exactly like the interpreter.
  private static int add(int a, int b) {
    return a + b;
  }

  private static int subtract(int a, int b) {
    return a - b;
  }

  private static int multiply(int a, int b) {
    return a * b;
  }

  private static int divide(int a, int b) {
    return a / b;
  }

  @ParameterizedTest(name = "{0} == {1}")
  @MethodSource("arithmetic")
  void arithmeticMatchesJava(String expression, int javaValue) {
    assertTrue(holds("", "(" + expression + ") == " + javaValue));
    assertFalse(holds("", "(" + expression + ") != " + javaValue));
  }

  @ParameterizedTest(name = "{0} is {1}")
  @CsvSource({
    "3 == 3, true",
    "3 != 3, false",
    "2 < 3, true",
    "3 < 3, false",
    "3 <= 3, true",
    "4 > 3, true",
    "3 > 3, false",
    "3 >= 4, false",
    "true == true, true",
    "true != false, true",
    "!true, false",
    "true && false, false",
    "true || false, true",
    "!(1 > 2) && 3 >= 3, true",
    "1 + 1 == 2 || 1 / 0 == 0, true"
  })
  void comparisonsAndLogic(String condition, boolean expected) {
    assertEquals(expected, holds("", condition));
  }

  @Test
  void variablesBooleansAndCompoundAssignments() {
    int x = 10;
    x -= 4;
    x *= 3;
    x /= 2;
    x %= 7;
    x += 3;
    --x;
    ++x;
    x++;
    assertTrue(
        holds(
            "int x = 10;\nx -= 4;\nx *= 3;\nx /= 2;\nx %= 7;\nx += 3;\n--x;\n++x;\nx++;",
            "x == " + x));
    assertTrue(holds("boolean b = false;\nb = !b;", "b"));
    assertTrue(holds("int a = 1, b = 2;\nint c;\nc = a + b;", "c == 3"));
  }

  @Test
  void intOverflowWrapsLikeJava() {
    assertTrue(holds("int m = 2147483647;\nm++;", "m == -2147483648"));
    assertTrue(holds("int m = -2147483648;\nm--;", "m == 2147483647"));
    assertTrue(holds("int m = -2147483648;\nm = -m;", "m == -2147483648"));
  }

  @Test
  void waveOneScriptFiresOnce() {
    FakeTurret turret = new FakeTurret();
    assertEquals(CycleOutcome.COMPLETED, ok("disparar();").run(turret));
    assertEquals(1, turret.shotsFired);
    assertEquals(11, turret.ammo);
  }

  @Test
  void ifElseTakesTheRightBranch() {
    CompiledScript script = ok("if (municion() == 0) {\n  recargar();\n} else {\n  disparar();\n}");
    FakeTurret loaded = new FakeTurret();
    assertEquals(CycleOutcome.COMPLETED, script.run(loaded));
    assertEquals(1, loaded.shotsFired);

    FakeTurret empty = new FakeTurret();
    empty.ammo = 0;
    assertEquals(CycleOutcome.RELOADING, script.run(empty));
    assertEquals(1, empty.reloadCalls);
    assertEquals(0, empty.fireCalls);
  }

  @Test
  void elseIfChains() {
    String source =
        "if (calor() > 50) {\n  recargar();\n} else if (calor() > 10) {\n  disparar();\n"
            + "  disparar();\n} else {\n  disparar();\n}";
    FakeTurret warm = new FakeTurret();
    warm.heat = 20;
    ok(source).run(warm);
    assertEquals(2, warm.shotsFired);
  }

  @Test
  void logicShortCircuitsLikeJava() {
    FakeTurret turret = new FakeTurret();
    ok("if (false && hayEnemigos()) disparar();\nif (true || hayEnemigos()) disparar();")
        .run(turret);
    assertEquals(0, turret.hasEnemiesCalls);
    ok("if (true && hayEnemigos()) disparar();").run(turret);
    assertEquals(1, turret.hasEnemiesCalls);
  }

  @Test
  void variablesStartFreshEveryCycle() {
    CompiledScript script = ok("int n = 0;\nn++;\nif (n == 1) disparar();");
    FakeTurret turret = new FakeTurret();
    script.run(turret);
    script.run(turret);
    assertEquals(2, turret.shotsFired);
  }

  @Test
  void whileRunsUntilTheConditionIsFalse() {
    FakeTurret turret = new FakeTurret();
    ok("int n = 0;\nwhile (n < 3) {\n  disparar();\n  n++;\n}").run(turret);
    assertEquals(3, turret.shotsFired);
  }

  @Test
  void burstWhileStopsBeforeOverheating() {
    FakeTurret turret = new FakeTurret();
    ok("while (calor() < 80 && municion() > 0) {\n  disparar();\n}").run(turret);
    assertEquals(4, turret.shotsFired);
    assertEquals(80, turret.heat);
  }

  @Test
  void reloadEndsTheScriptRightThere() {
    FakeTurret turret = new FakeTurret();
    assertEquals(CycleOutcome.RELOADING, ok("disparar();\nrecargar();\ndisparar();").run(turret));
    assertEquals(1, turret.shotsFired);
    assertEquals(1, turret.reloadCalls);
    assertNull(ok("recargar();").faultType());
  }

  @Test
  void enemyReadsGoThroughTheTurret() {
    FakeTurret turret = new FakeTurret();
    turret.nearest = turret.addEnemy(3, 44, 80, true, 1200);
    String source =
        """
        Enemigo e = enemigoMasCercano();
        if (e.vida() == 44 && e.velocidad() == 80 && e.blindado() && e.distanciaAlNucleo() == 1200) {
          disparar();
        }
        """;
    ok(source).run(turret);
    assertEquals(1, turret.shotsFired);
  }

  @Test
  void aimAndTargetKeepTheTargetAcrossCycles() {
    FakeTurret turret = new FakeTurret();
    long first = turret.addEnemy(1, 10, 100, false, 900);
    long second = turret.addEnemy(2, 10, 100, false, 500);
    String source =
        """
        Enemigo o = objetivo();
        if (o == null) {
          o = enemigoMasCercano();
          apuntar(o);
        }
        disparar();
        """;
    CompiledScript script = ok(source);
    turret.nearest = first;
    script.run(turret);
    assertEquals(first, turret.target);
    turret.nearest = second;
    script.run(turret);
    assertEquals(first, turret.target, "objetivo() kept the first enemy");
    assertEquals(first, turret.lastAimed);
  }

  @Test
  void referenceEqualityComparesHandlesWithoutTouchingTheEnemy() {
    FakeTurret turret = new FakeTurret();
    long enemy = turret.addEnemy(1, 10, 100, false, 900);
    turret.nearest = enemy;
    turret.target = enemy;
    ok("if (objetivo() == enemigoMasCercano() && objetivo() != null) disparar();").run(turret);
    assertEquals(1, turret.shotsFired);

    turret.kill(enemy);
    // Comparing a dead enemy is fine in Java: only calling a method on it fails.
    assertEquals(
        CycleOutcome.COMPLETED,
        ok("Enemigo e = enemigoMasCercano();\nif (e != null) disparar();").run(turret));
  }

  @Test
  void ammoSwitchSelectsTheType() {
    FakeTurret turret = new FakeTurret();
    ok("usarMunicion(PERFORANTE);").run(turret);
    assertEquals(AmmoType.PIERCING, turret.ammoType);
    ok("usarMunicion(NORMAL);").run(turret);
    assertEquals(AmmoType.NORMAL, turret.ammoType);
  }

  @Test
  void callsWhoseValueIsIgnoredStillRun() {
    FakeTurret turret = new FakeTurret();
    ok("hayEnemigos();\nmunicion();\nenemigoMasCercano();").run(turret);
    assertEquals(1, turret.hasEnemiesCalls);
  }

  @Test
  void nullStartsAsNoEnemy() {
    FakeTurret turret = new FakeTurret();
    turret.nearest = EnemyHandle.NONE;
    ok("Enemigo e = enemigoMasCercano();\nif (e == null) disparar();").run(turret);
    assertEquals(1, turret.shotsFired);
  }
}
