package io.github.yeray06.wavedefense.script;

import static io.github.yeray06.wavedefense.script.Scripts.ALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.diag.CompileError;
import io.github.yeray06.wavedefense.script.diag.ErrorCode;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** No input may crash the compiler, hang it, or flood the editor with errors. */
class RobustnessTest {
  private static final String[] FRAGMENTS = {
    "disparar",
    "recargar",
    "municion",
    "Enemigo",
    "e",
    "x",
    "int",
    "boolean",
    "if",
    "else",
    "while",
    "for",
    "try",
    "class",
    "new",
    "return",
    "null",
    "true",
    "0",
    "1",
    "2147483648",
    "1.5",
    "\"s\"",
    "'c'",
    "(",
    ")",
    "{",
    "}",
    "[",
    "]",
    ";",
    ",",
    ".",
    "=",
    "==",
    "+",
    "-",
    "*",
    "/",
    "%",
    "++",
    "--",
    "+=",
    "&&",
    "||",
    "!",
    "<",
    ">=",
    "?",
    ":",
    "&",
    "~",
    "@",
    "#",
    "/*",
    "*/",
    "//",
    "\n",
    " ",
    "\t",
    "\r"
  };

  @Test
  void randomTokenSoupNeverThrowsAndAlwaysEnds() {
    Random random = new Random(20261002L);
    for (int i = 0; i < 3_000; i++) {
      StringBuilder source = new StringBuilder();
      int length = random.nextInt(60);
      for (int j = 0; j < length; j++) {
        source.append(FRAGMENTS[random.nextInt(FRAGMENTS.length)]).append(' ');
      }
      CompileResult result = Scripts.compile(source.toString());
      assertTrue(
          result.internalError().isEmpty(),
          () -> "internal error for: " + source + " -> " + result.internalError());
    }
  }

  @Test
  void randomCharactersNeverThrow() {
    Random random = new Random(7L);
    for (int i = 0; i < 2_000; i++) {
      char[] chars = new char[random.nextInt(80)];
      for (int j = 0; j < chars.length; j++) {
        chars[j] = (char) (random.nextInt(0x3000));
      }
      CompileResult result = Scripts.compile(new String(chars));
      assertTrue(result.internalError().isEmpty());
    }
  }

  @Test
  void absurdNestingIsAnErrorNotAStackOverflow() {
    for (String source :
        List.of(
            "{".repeat(100_000),
            "if (true) ".repeat(100_000) + "disparar();",
            "int x = " + "(".repeat(100_000) + "1;",
            "boolean b = " + "!".repeat(100_000) + "true;",
            "int x = " + "-(".repeat(50_000) + "1;",
            "int x;\nx = " + "x = ".repeat(100_000) + "1;")) {
      CompileResult result = Scripts.compile(source);
      assertTrue(result.internalError().isEmpty(), () -> String.valueOf(result.internalError()));
      assertTrue(
          result.errors().stream().anyMatch(e -> e.code() == ErrorCode.NESTING_TOO_DEEP),
          () -> Scripts.describe(result.errors()));
    }
  }

  @Test
  void hugeFlatScriptsAreFineAndErrorsAreCapped() {
    CompileResult result = Scripts.COMPILER.compile("saltar();\n".repeat(20_000), ALL, 12);
    assertTrue(result.internalError().isEmpty());
    assertEquals(50, result.errors().size());
    assertEquals(20_000, result.memoryUsed());
  }

  @Test
  void emptyAndBlankScriptsCompileAndDoNothing() {
    for (String source : List.of("", "   \n\n", "// nada", "/* nada */", ";;;", "{ }")) {
      FakeTurret turret = new FakeTurret();
      Scripts.ok(source).run(turret);
      assertEquals(0, turret.fireCalls);
    }
  }

  @Test
  void systemExitCannotEvenBeNamed() {
    List<CompileError> errors = Scripts.compile("System.exit(0);").errors();
    assertEquals(1, errors.size());
    assertEquals(ErrorCode.UNKNOWN_SYMBOL, errors.get(0).code());
    assertEquals(List.of("System"), errors.get(0).args());
  }

  @Test
  void lexerErrorsDoNotCascadeIntoParserErrors() {
    List<CompileError> errors = Scripts.compile("int x = 1.5;\nint y = \"hola\";").errors();
    assertEquals(2, errors.size(), () -> Scripts.describe(errors));
    assertNotEquals(errors.get(0).line(), errors.get(1).line());
  }
}
