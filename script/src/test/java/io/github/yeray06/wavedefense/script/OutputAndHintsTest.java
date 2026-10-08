package io.github.yeray06.wavedefense.script;

import static io.github.yeray06.wavedefense.script.Scripts.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.api.EnemyHandle;
import io.github.yeray06.wavedefense.script.api.PrintKind;
import io.github.yeray06.wavedefense.script.diag.CompileError;
import io.github.yeray06.wavedefense.script.diag.CycleOutcome;
import io.github.yeray06.wavedefense.script.diag.ErrorCode;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import java.util.List;
import org.junit.jupiter.api.Test;

/** imprimir(), saludar(), oleada() and the «¿Quisiste decir…?» suggestions. */
class OutputAndHintsTest {

  private static FakeTurret run(CompiledScript script) {
    FakeTurret turret = new FakeTurret();
    turret.nearest = turret.addEnemy(3, 40, 100, false, 500);
    turret.wave = 7;
    assertEquals(CycleOutcome.COMPLETED, script.run(turret));
    return turret;
  }

  @Test
  void imprimirWritesEveryKindOfValue() {
    FakeTurret t = run(ok("imprimir(municion() + 1);"));
    assertEquals(PrintKind.INT, t.lastPrintKind);
    assertEquals(13, t.lastPrintValue);
    assertEquals(1, t.lastPrintLine);

    t = run(ok("disparar();\nimprimir(hayEnemigos());"));
    assertEquals(PrintKind.BOOLEAN, t.lastPrintKind);
    assertEquals(1, t.lastPrintValue);
    assertEquals(2, t.lastPrintLine);

    t = run(ok("imprimir(enemigoMasCercano());"));
    assertEquals(PrintKind.ENEMY, t.lastPrintKind);
    assertEquals(3, t.lastPrintValue);

    t = run(ok("Enemigo e = null;\nimprimir(e);\nimprimir(null);"));
    assertEquals(PrintKind.NULL, t.lastPrintKind);
    assertEquals(EnemyHandle.NONE, t.lastPrintValue);
    assertEquals(2, t.prints);

    t = run(ok("imprimir(enemigos());"));
    assertEquals(PrintKind.LIST, t.lastPrintKind);
    assertEquals(1, t.lastPrintValue, "a list prints how many it holds");
  }

  @Test
  void imprimirTakesNoMemory() {
    CompileResult result = Scripts.compile("disparar();\nimprimir(calor());\nimprimir(1);");
    assertEquals(1, result.memoryUsed());
  }

  @Test
  void imprimirInsideAMethodSaysSo() {
    FakeTurret t = run(ok("ver();", "void ver() {\n  imprimir(oleada());\n}"));
    assertEquals(7, t.lastPrintValue);
    assertEquals(2, t.lastPrintLine);
    assertTrue(t.lastPrintInLibrary);
  }

  @Test
  void saludarAndOleada() {
    FakeTurret t = run(ok("saludar();\nsaludar();\nif (oleada() == 7) disparar();"));
    assertEquals(2, t.greetings);
    assertEquals(1, t.shotsFired);
  }

  private static CompileError only(String source) {
    List<CompileError> errors = Scripts.compile(source).errors();
    assertEquals(1, errors.size(), () -> Scripts.describe(errors));
    return errors.get(0);
  }

  @Test
  void aMistypedCallSuggestsTheRightOne() {
    CompileError e = only("disprar();");
    assertEquals(ErrorCode.UNKNOWN_METHOD, e.code());
    assertEquals(List.of("disprar", "disparar"), e.args());
    assertEquals(List.of("Recargar", "recargar"), only("Recargar();").args(), "case alone");
  }

  @Test
  void mistypedVariablesTypesAndEnemyCallsToo() {
    assertEquals(
        List.of("objetvo", "objetivo"), only("Enemigo objetivo = null;\napuntar(objetvo);").args());
    assertEquals(List.of("PERFORANT", "PERFORANTE"), only("usarMunicion(PERFORANT);").args());
    assertEquals(List.of("Enemgo", "Enemigo"), only("Enemgo e = null;").args());
    assertEquals(
        List.of("vidaa", "Enemigo", "vida"),
        only("Enemigo e = enemigoMasCercano();\nint v = e.vidaa();").args());
    assertEquals(
        List.of("lenght", "List<Enemigo>", ""), only("int n = enemigos().lenght();").args());
  }

  @Test
  void aMadeUpNameGetsNoSuggestion() {
    assertEquals(List.of("teletransportar", ""), only("teletransportar();").args());
    assertFalse(only("int x = zzz;").args().get(1).toString().length() > 0);
  }
}
