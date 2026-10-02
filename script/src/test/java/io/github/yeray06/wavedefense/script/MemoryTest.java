package io.github.yeray06.wavedefense.script;

import static io.github.yeray06.wavedefense.script.Scripts.ALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.diag.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** One memory line per statement (D-06). */
class MemoryTest {
  private static final int TURRET_MEMORY = 12;

  private static CompileResult compile(String source) {
    return Scripts.COMPILER.compile(source, ALL, TURRET_MEMORY);
  }

  @Test
  void plansIfElseExampleUsesThreeLines() {
    CompileResult result =
        compile("if (municion() == 0) {\n    recargar();\n} else {\n    disparar();\n}");
    assertEquals(3, result.memoryUsed());
    assertEquals(Map.of(1, 1, 2, 1, 4, 1), result.memoryByLine());
  }

  @Test
  void bracesElseCommentsAndBlankLinesAreFree() {
    String source =
        """
        // comentario

        {
          /* otro */
        }
        ;
        if (hayEnemigos()) {
          disparar();
        }
        else
        {
          recargar();
        }
        """;
    assertEquals(3, compile(source).memoryUsed());
  }

  @Test
  void formattingDoesNotChangeTheCount() {
    String oneLine = "if (municion() == 0) { recargar(); } else { disparar(); }";
    CompileResult result = compile(oneLine);
    assertEquals(3, result.memoryUsed());
    assertEquals(Map.of(1, 3), result.memoryByLine());
  }

  @Test
  void eachKindOfStatementCountsOne() {
    String source =
        """
        int a = 1, b = 2;
        a = b;
        a++;
        a += 3;
        disparar();
        while (a < 10) {
          a++;
        }
        if (a > 3) disparar(); else if (a > 2) recargar();
        """;
    // decl, assign, ++, +=, call, while, ++, if, call, else-if, call
    assertEquals(11, compile(source).memoryUsed());
  }

  @Test
  void twelveStatementsFitAndTheThirteenthIsReportedWhereItIs() {
    assertTrue(compile("disparar();\n".repeat(TURRET_MEMORY)).succeeded());

    CompileResult over = compile("disparar();\n".repeat(TURRET_MEMORY + 2));
    assertEquals(TURRET_MEMORY + 2, over.memoryUsed());
    assertEquals(1, over.errors().size());
    assertEquals(ErrorCode.MEMORY_EXCEEDED, over.errors().get(0).code());
    assertEquals(TURRET_MEMORY + 1, over.errors().get(0).line());
    assertEquals(java.util.List.of(TURRET_MEMORY + 2, TURRET_MEMORY), over.errors().get(0).args());
  }

  @Test
  void memoryIsCountedEvenWhenTheScriptHasErrors() {
    CompileResult result = compile("disparar();\nsaltar();\nrecargar();");
    assertTrue(!result.succeeded());
    assertEquals(3, result.memoryUsed());
  }

  @Test
  void limitComesFromTheCaller() {
    String sixteen = "disparar();\n".repeat(16);
    assertTrue(Scripts.COMPILER.compile(sixteen, ALL, 16).succeeded());
    assertTrue(!Scripts.COMPILER.compile(sixteen, ALL, 12).succeeded());
  }
}
