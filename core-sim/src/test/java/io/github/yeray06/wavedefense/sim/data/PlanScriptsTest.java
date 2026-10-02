package io.github.yeray06.wavedefense.sim.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.CompileResult;
import io.github.yeray06.wavedefense.script.ScriptCompiler;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.script.diag.ErrorCode;
import io.github.yeray06.wavedefense.sim.data.ScriptApiLoader.ScriptApiData;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The plan's own example scripts, compiled with the shipped vocabulary. */
class PlanScriptsTest {
  /** The plan's script memory ("Memoria: 12 líneas por script"). */
  private static final int PLAN_MEMORY = 12;

  private static ScriptCompiler compiler;

  @BeforeAll
  static void load() throws IOException {
    ScriptApiData data = ScriptApiLoader.load(ScriptApiLoaderTest.realFile());
    compiler = new ScriptCompiler(data.api(), data.limits());
  }

  private static CompileResult compile(String source, Feature... features) {
    return compiler.compile(source, Unlocks.of(1, features), PLAN_MEMORY);
  }

  @Test
  void waveOneIsFree() {
    assertTrue(compile("disparar();").succeeded());
  }

  @Test
  void waveThreeNeedsIfAndUsesThreeLines() {
    String source =
        """
        if (municion() == 0) {
            recargar();
        } else {
                disparar();
        }
        """;
    assertTrue(!compile(source).succeeded());
    CompileResult result = compile(source, Feature.IF_ELSE);
    assertTrue(result.succeeded());
    assertEquals(3, result.memoryUsed());
  }

  @Test
  void waveTwelveNeedsFor() {
    String source =
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
    CompileResult result =
        compile(source, Feature.IF_ELSE, Feature.VARIABLES, Feature.LOGIC_OPS, Feature.WHILE);
    assertEquals(1, result.errors().size());
    assertEquals(ErrorCode.FEATURE_LOCKED, result.errors().get(0).code());
    assertEquals(List.of(Feature.FOR), result.errors().get(0).args());
  }

  @Test
  void exceptionTableFixesCompile() {
    // Plan, "Excepciones", column "Cómo se resuelve".
    assertTrue(compile("if (municion() == 0)\nrecargar();", Feature.IF_ELSE).succeeded());
    assertTrue(compile("if (calor() < 80) disparar();", Feature.IF_ELSE).succeeded());
    assertTrue(
        compile(
                "Enemigo objetivo = objetivo();\nif (objetivo != null)\napuntar(objetivo);",
                Feature.IF_ELSE,
                Feature.VARIABLES)
            .succeeded());
  }
}
