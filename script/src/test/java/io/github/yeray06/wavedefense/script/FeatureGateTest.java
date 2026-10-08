package io.github.yeray06.wavedefense.script;

import static io.github.yeray06.wavedefense.script.Scripts.NOTHING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.script.diag.CompileError;
import io.github.yeray06.wavedefense.script.diag.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What each shop piece unlocks (D-07) and how locked syntax is reported. */
class FeatureGateTest {
  private static final Unlocks IF_ONLY = Unlocks.of(1, Feature.IF_ELSE);
  private static final Unlocks UP_TO_VARIABLES = Unlocks.of(1, Feature.IF_ELSE, Feature.VARIABLES);
  private static final Unlocks UP_TO_LOGIC =
      Unlocks.of(1, Feature.IF_ELSE, Feature.VARIABLES, Feature.LOGIC_OPS);

  @Test
  void waveOneScriptCompilesWithNothingBought() {
    assertTrue(Scripts.compile("disparar();\nrecargar();", NOTHING).succeeded());
  }

  @Test
  void ifElseUnlocksConditionsComparisonsLiteralsAndTurretReads() {
    String plansWaveThreeScript =
        """
        if (municion() == 0) {
            recargar();
        } else {
                disparar();
        }
        """;
    assertTrue(Scripts.compile(plansWaveThreeScript, IF_ONLY).succeeded());
    assertTrue(Scripts.compile("if (calor() < 80) disparar();", IF_ONLY).succeeded());
    assertTrue(Scripts.compile("if (hayEnemigos() == true) disparar();", IF_ONLY).succeeded());
    assertTrue(Scripts.compile("if (calor() >= -5) disparar();", IF_ONLY).succeeded());
  }

  @Test
  void withoutIfElseTheWaveThreeScriptReportsTheLockOncePerLine() {
    List<CompileError> errors =
        Scripts.compile("if (municion() == 0) {\n  recargar();\n}", NOTHING).errors();
    assertEquals(1, errors.size(), () -> Scripts.describe(errors));
    assertEquals(ErrorCode.FEATURE_LOCKED, errors.get(0).code());
    assertEquals(List.of(Feature.IF_ELSE), errors.get(0).args());
  }

  @Test
  void callsReportWhichPieceUnlocksThem() {
    CompileError error =
        Scripts.expectError("municion();", NOTHING, ErrorCode.API_LOCKED_FEATURE, 1);
    assertEquals(List.of("municion", Feature.IF_ELSE), error.args());
  }

  @Test
  void variablesUnlockDeclarationsArithmeticNullAndEnemyCalls() {
    String source =
        """
        Enemigo e = enemigoMasCercano();
        int disparos = 0;
        disparos++;
        disparos += 2 * 3 - 1;
        disparos = disparos / 2 % 3;
        if (e != null) {
          apuntar(e);
          int v = e.vida() + e.velocidad() + e.distanciaAlNucleo();
          if (e.blindado() == false) {
            disparar();
          }
        }
        Enemigo o = objetivo();
        """;
    assertTrue(Scripts.compile(source, UP_TO_VARIABLES).succeeded());
    Scripts.expectError(source, IF_ONLY, ErrorCode.FEATURE_LOCKED, 1);
  }

  @Test
  void eachVariablesConstructIsLockedWithoutIt() {
    for (String source :
        List.of(
            "int x = 1;",
            "Enemigo e = null;",
            "apuntar(enemigoMasCercano());",
            "if (calor() + 1 > 3) disparar();",
            "if (-calor() < 3) disparar();")) {
      List<CompileError> errors = Scripts.compile(source, IF_ONLY).errors();
      assertTrue(
          errors.stream()
              .anyMatch(
                  e ->
                      e.args().contains(Feature.VARIABLES)
                          && (e.code() == ErrorCode.FEATURE_LOCKED
                              || e.code() == ErrorCode.API_LOCKED_FEATURE)),
          () -> source + " -> " + Scripts.describe(errors));
    }
  }

  @Test
  void logicOperatorsNeedTheirPiece() {
    for (String source :
        List.of(
            "if (hayEnemigos() && calor() < 80) disparar();",
            "if (hayEnemigos() || calor() < 80) disparar();",
            "if (!hayEnemigos()) recargar();")) {
      CompileError error =
          Scripts.expectError(source, UP_TO_VARIABLES, ErrorCode.FEATURE_LOCKED, 1);
      assertEquals(List.of(Feature.LOGIC_OPS), error.args());
      assertTrue(Scripts.compile(source, UP_TO_LOGIC).succeeded(), source);
    }
  }

  @Test
  void whileShowsThePlansMessageArgument() {
    CompileError error =
        Scripts.expectError(
            "while (calor() < 60) {\n  disparar();\n}", UP_TO_LOGIC, ErrorCode.FEATURE_LOCKED, 1);
    assertEquals(List.of(Feature.WHILE), error.args());
  }

  @Test
  void ammoSwitchUnlocksAtWaveSixNotInTheShop() {
    String source = "usarMunicion(PERFORANTE);\nusarMunicion(NORMAL);";
    CompileError error = Scripts.expectError(source, Unlocks.of(5), ErrorCode.API_LOCKED_WAVE, 1);
    assertEquals(List.of("usarMunicion", TestApi.WAVE_FOR_AMMO), error.args());
    assertTrue(Scripts.compile(source, Unlocks.of(6)).succeeded());
  }

  @Test
  void syntaxOutsideTheMvpIsReportedAsLockedAndSkippedWhole() {
    String plansWaveTwelveScript =
        """
        Enemigo objetivo = null;
        for (Enemigo e : enemigos()) {
            if (objetivo == null || e.distanciaAlNucleo() <
        objetivo.distanciaAlNucleo()) {
                        objetivo = e;
                }
        }
        if (objetivo != null) {
            apuntar(objetivo);
                disparar();
        }
        """;
    List<CompileError> errors = Scripts.compile(plansWaveTwelveScript, Scripts.MVP).errors();
    assertEquals(1, errors.size(), () -> Scripts.describe(errors));
    assertEquals(ErrorCode.FEATURE_LOCKED, errors.get(0).code());
    assertEquals(List.of(Feature.FOR), errors.get(0).args());
    assertEquals(2, errors.get(0).line());
    assertTrue(
        Scripts.compile(plansWaveTwelveScript).succeeded(), "the plan's script, with for bought");
  }

  @Test
  void listsComeWithFor() {
    for (String source :
        List.of(
            "List<Enemigo> lista = null;",
            "int n = enemigos().size();",
            "for (int i = 0; i < 3; i++) {\n  disparar();\n}")) {
      List<CompileError> errors = Scripts.compile(source, Scripts.MVP).errors();
      assertTrue(
          errors.stream().anyMatch(e -> e.args().contains(Feature.FOR)),
          () -> source + " -> " + Scripts.describe(errors));
      assertTrue(Scripts.compile(source).succeeded(), source);
    }
  }

  @Test
  void methodsTryAndClassesAreLockedFeatures() {
    assertLocked("void ayuda() {\n}", Feature.METHODS);
    assertLocked("public static void ayuda() {\n}", Feature.METHODS);
    assertLocked("int doble(int x) {\n  return x * 2;\n}", Feature.METHODS);
    assertLocked("return;", Feature.METHODS);
    assertLocked(
        "try {\n  disparar();\n} catch (Exception e) {\n} finally {\n}", Feature.TRY_CATCH);
    assertLocked("class Torreta {\n}", Feature.CLASSES);
    assertLocked("Enemigo e = new Enemigo();", Feature.CLASSES);
  }

  private static void assertLocked(String source, Feature feature) {
    List<CompileError> errors = Scripts.compile(source).errors();
    assertTrue(
        errors.stream()
            .anyMatch(
                e -> e.code() == ErrorCode.FEATURE_LOCKED && e.args().equals(List.of(feature))),
        () -> source + " -> " + Scripts.describe(errors));
  }
}
