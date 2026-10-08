package io.github.yeray06.wavedefense.script;

import static io.github.yeray06.wavedefense.script.Scripts.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.diag.CycleOutcome;
import io.github.yeray06.wavedefense.script.diag.ErrorCode;
import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import org.junit.jupiter.api.Test;

/** The library's methods: declared once, called from any turret, run on a fake turret. */
class MethodTest {

  /** Fires {@code n} times: lets a test read an int result through the turret. */
  private static final String FIRE_N =
      """
      void dispararVeces(int n) {
        for (int i = 0; i < n; i++) {
          disparar();
        }
      }
      """;

  private static int shots(CompiledScript script) {
    FakeTurret turret = new FakeTurret();
    turret.ammo = 1000;
    turret.heatPerShot = 0;
    runs(script, turret);
    return turret.shotsFired;
  }

  private static void runs(CompiledScript script, FakeTurret turret) {
    assertEquals(
        CycleOutcome.COMPLETED,
        script.run(turret),
        () -> script.faultType() + " at " + script.faultLine());
  }

  @Test
  void aVoidMethodRunsWhereItIsCalled() {
    String library = "void rafaga() {\n  disparar();\n  disparar();\n}";
    assertEquals(4, shots(ok("rafaga();\nrafaga();", library)));
  }

  @Test
  void parametersAndReturnValuesWorkForEveryType() {
    String library =
        FIRE_N
            + """
            int doble(int x) {
              return x * 2;
            }
            boolean par(int x) {
              return x % 2 == 0;
            }
            Enemigo elMismo(Enemigo e) {
              return e;
            }
            List<Enemigo> todos() {
              return enemigos();
            }
            """;
    assertEquals(6, shots(ok("dispararVeces(doble(3));", library)));
    assertEquals(1, shots(ok("if (par(4) && !par(3)) disparar();", library)));
    assertEquals(
        1, shots(ok("if (elMismo(null) == null && todos().isEmpty()) disparar();", library)));
  }

  @Test
  void methodsCallEachOtherInAnyOrder() {
    String library =
        FIRE_N
            + """
            int triple(int x) {
              return doble(x) + x;
            }
            int doble(int x) {
              return x + x;
            }
            """;
    assertEquals(9, shots(ok("dispararVeces(triple(3));", library)));
  }

  @Test
  void argumentsAreEvaluatedLeftToRightAndNeverOverwritten() {
    // The second argument calls a method, which must not clobber the first one, already
    // evaluated.
    String library =
        FIRE_N
            + """
            int resta(int a, int b) {
              return a - b;
            }
            int cinco() {
              int x = 5;
              int y = 0;
              return x + y;
            }
            """;
    assertEquals(4, shots(ok("dispararVeces(resta(9, cinco()));", library)));
  }

  @Test
  void eachCallHasItsOwnVariables() {
    String library =
        FIRE_N
            + """
            int factorial(int n) {
              if (n <= 1) {
                return 1;
              }
              return n * factorial(n - 1);
            }
            int fib(int n) {
              if (n < 2) {
                return n;
              }
              return fib(n - 1) + fib(n - 2);
            }
            """;
    assertEquals(24, shots(ok("dispararVeces(factorial(4));", library)));
    assertEquals(13, shots(ok("dispararVeces(fib(7));", library)));
  }

  @Test
  void theCallersVariablesSurviveTheCall() {
    String library = "int pisa() {\n  int a = 99;\n  int b = 98;\n  return 0;\n}";
    String script = "int a = 2;\nint b = 3;\nint c = pisa();\nif (a == 2 && b == 3) disparar();";
    assertEquals(1, shots(ok(script, library)));
  }

  @Test
  void returnEndsTheMethodAndReturnInTheScriptEndsTheCycle() {
    String library = "void unaVez() {\n  disparar();\n  return;\n}";
    assertEquals(1, shots(ok("unaVez();", library)));
    assertEquals(1, shots(ok("disparar();\nif (hayEnemigos() == false) return;\ndisparar();", "")));
  }

  @Test
  void returnInsideALoopLeavesTheLoop() {
    String library =
        FIRE_N
            + """
            int primeroMayor(int limite) {
              int i = 0;
              while (true) {
                if (i * i > limite) {
                  return i;
                }
                i++;
              }
            }
            """;
    assertEquals(4, shots(ok("dispararVeces(primeroMayor(10));", library)));
  }

  @Test
  void endlessRecursionRaisesStackOverflowAtTheLibraryLine() {
    CompiledScript script = ok("disparar();\nsinFin();", "void sinFin() {\n  sinFin();\n}");
    assertEquals(CycleOutcome.FAULT, script.run(new FakeTurret()));
    assertEquals(FaultType.STACK_OVERFLOW, script.faultType());
    assertEquals(2, script.faultLine());
    assertTrue(script.faultInLibrary());
  }

  @Test
  void aFaultInsideAMethodIsReportedAtTheLibraryLine() {
    CompiledScript script =
        ok("disparar();\napuntarA(null);", "void apuntarA(Enemigo e) {\n  apuntar(e);\n}");
    assertEquals(CycleOutcome.FAULT, script.run(new FakeTurret()));
    assertEquals(FaultType.NULL_POINTER, script.faultType());
    assertEquals(2, script.faultLine());
    assertTrue(script.faultInLibrary());

    CompiledScript own = ok("Enemigo e = null;\napuntar(e);", "");
    assertEquals(CycleOutcome.FAULT, own.run(new FakeTurret()));
    assertFalse(own.faultInLibrary(), "a fault in the script is the script's");
  }

  @Test
  void aReloadInsideAMethodEndsTheCycle() {
    CompiledScript script =
        ok("cargar();\ndisparar();", "void cargar() {\n  if (municion() < 20) recargar();\n}");
    FakeTurret turret = new FakeTurret();
    assertEquals(CycleOutcome.RELOADING, script.run(turret));
    assertEquals(0, turret.shotsFired);
  }

  @Test
  void theScriptRunsAgainAfterAFaultInsideAMethod() {
    CompiledScript script = ok("int x = 1;\nsinFin();", "void sinFin() {\n  sinFin();\n}");
    FakeTurret turret = new FakeTurret();
    assertEquals(CycleOutcome.FAULT, script.run(turret));
    assertEquals(CycleOutcome.FAULT, script.run(turret), "the frame is reset every cycle");
  }

  @Test
  void aBranchThatReturnsDoesNotLeaveVariablesUnassigned() {
    String library =
        """
        int valor(boolean b) {
          int x;
          if (b) {
            x = 1;
          } else {
            return 0;
          }
          return x;
        }
        """;
    Scripts.okLibrary(library);
  }

  @Test
  void anEndlessLoopNeedsNoReturnAfterIt() {
    Scripts.okLibrary("int nunca() {\n  while (true) {\n    disparar();\n  }\n}");
    Scripts.okLibrary("int nunca() {\n  for (;;) {\n  }\n}");
  }

  @Test
  void libraryLinesTakeNoMemory() {
    String library = "void largo() {\n" + "  disparar();\n".repeat(40) + "}";
    CompileResult result =
        Scripts.COMPILER.compile("largo();", Scripts.okLibrary(library), Scripts.METHODS, 12);
    assertTrue(result.succeeded());
    assertEquals(1, result.memoryUsed());
  }

  @Test
  void theSameLibraryRunsForSeveralTurretsAtOnce() {
    var library = Scripts.okLibrary(FIRE_N);
    CompiledScript a = Scripts.compile("dispararVeces(2);", library).script().orElseThrow();
    CompiledScript b = Scripts.compile("dispararVeces(3);", library).script().orElseThrow();
    FakeTurret first = new FakeTurret();
    FakeTurret second = new FakeTurret();
    runs(a, first);
    runs(b, second);
    runs(a, first);
    assertEquals(4, first.shotsFired);
    assertEquals(3, second.shotsFired);
  }

  @Test
  void aBrokenLibraryNamesTheMethodInEveryScriptThatUsesIt() {
    var broken = Scripts.library("void rafaga() {\n  saltar();\n}");
    assertFalse(broken.succeeded());
    Scripts.expectOnlyWith("rafaga();", broken.library(), ErrorCode.LIBRARY_HAS_ERRORS, 1);
  }

  @Test
  void methodsAreLockedUntilBought() {
    var result = Scripts.COMPILER.compileLibrary("void a() {\n}", Scripts.ALL);
    assertEquals(ErrorCode.FEATURE_LOCKED, result.errors().get(0).code());
  }
}
