package io.github.yeray06.wavedefense.script;

import static io.github.yeray06.wavedefense.script.Scripts.ALL;
import static io.github.yeray06.wavedefense.script.Scripts.NOTHING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.script.check.Library;
import io.github.yeray06.wavedefense.script.diag.CompileError;
import io.github.yeray06.wavedefense.script.diag.ErrorCode;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** At least one script per compile error, with the line where it must be reported. */
class ErrorCatalogTest {

  /**
   * @param library the library the script is compiled against, or {@code null} for none
   * @param inLibrary whether {@code source} is itself the library, where the error must be
   */
  record Case(
      ErrorCode code, String source, int line, Unlocks unlocks, String library, boolean inLibrary) {
    Case(ErrorCode code, String source, int line) {
      this(code, source, line, ALL);
    }

    Case(ErrorCode code, String source, int line, Unlocks unlocks) {
      this(code, source, line, unlocks, null, false);
    }

    /** An error in a library. */
    static Case library(ErrorCode code, String source, int line) {
      return new Case(code, source, line, Scripts.METHODS, null, true);
    }

    /** An error in a turret's script that calls {@code library}. */
    static Case calling(ErrorCode code, String library, String source, int line) {
      return new Case(code, source, line, Scripts.METHODS, library, false);
    }

    /** The same case with methods still locked. */
    Case locked() {
      return new Case(code, source, line, ALL, library, inLibrary);
    }

    @Override
    public String toString() {
      return code + ": " + source.replace("\n", "⏎");
    }
  }

  static Stream<Case> cases() {
    return Stream.of(
        new Case(ErrorCode.UNEXPECTED_CHARACTER, "disparar(); #", 1),
        new Case(ErrorCode.UNTERMINATED_COMMENT, "disparar();\n/* sin cerrar", 2),
        new Case(ErrorCode.INTEGER_TOO_LARGE, "int x = 99999999999;", 1),
        new Case(ErrorCode.INTEGER_TOO_LARGE, "int x = 2147483648;", 1),
        new Case(ErrorCode.NUMBER_FORMAT_NOT_SUPPORTED, "int x = 0x1F;", 1),
        new Case(ErrorCode.NUMBER_FORMAT_NOT_SUPPORTED, "int x = 010;", 1),
        new Case(ErrorCode.NUMBER_FORMAT_NOT_SUPPORTED, "int x = 5L;", 1),
        new Case(ErrorCode.DECIMAL_NOT_SUPPORTED, "int x = 1.5;", 1),
        new Case(ErrorCode.DECIMAL_NOT_SUPPORTED, "int x = 2f;", 1),
        new Case(ErrorCode.STRING_NOT_SUPPORTED, "int x = \"hola\";", 1),
        new Case(ErrorCode.CHAR_NOT_SUPPORTED, "int x = 'a';", 1),
        new Case(ErrorCode.EXPECTED_TOKEN, "if (hayEnemigos() disparar();", 1),
        new Case(ErrorCode.EXPECTED_TOKEN, "disparar()\nrecargar();", 1),
        new Case(ErrorCode.EXPECTED_TOKEN, "if (hayEnemigos()) {\n  disparar();", 2),
        new Case(ErrorCode.EXPECTED_EXPRESSION, "int x = ;", 1),
        new Case(ErrorCode.EXPECTED_NAME, "int = 3;", 1),
        new Case(ErrorCode.ELSE_WITHOUT_IF, "disparar();\nelse recargar();", 2),
        new Case(ErrorCode.NOT_A_STATEMENT, "int x = 1;\nx + 1;", 2),
        new Case(ErrorCode.DECLARATION_NOT_ALLOWED_HERE, "if (hayEnemigos()) int x = 1;", 1),
        new Case(ErrorCode.NESTING_TOO_DEEP, "{".repeat(100), 1),
        new Case(ErrorCode.NESTING_TOO_DEEP, "int x = " + "(".repeat(100) + "1;", 1),
        new Case(ErrorCode.UNSUPPORTED_KEYWORD, "while (true) {\n  break;\n}", 2),
        new Case(ErrorCode.UNSUPPORTED_KEYWORD, "switch (3) { }", 1),
        new Case(ErrorCode.UNSUPPORTED_OPERATOR, "int x = 1 & 2;", 1),
        new Case(ErrorCode.UNSUPPORTED_OPERATOR, "int x = true ? 1 : 2;", 1),
        new Case(ErrorCode.UNSUPPORTED_OPERATOR, "int x = ~1;", 1),
        new Case(ErrorCode.CAST_NOT_SUPPORTED, "int x = (int) 3;", 1),
        new Case(ErrorCode.UNSUPPORTED_TYPE, "double x = 1;", 1),
        new Case(ErrorCode.UNSUPPORTED_TYPE, "Municion m = NORMAL;", 1),
        new Case(
            ErrorCode.FEATURE_LOCKED,
            "for (int i = 0; i < 3; i++) {\n  disparar();\n}",
            1,
            Scripts.MVP),
        new Case(ErrorCode.ARRAYS_NOT_SUPPORTED, "int[] lista;", 1),
        new Case(ErrorCode.ARRAYS_NOT_SUPPORTED, "Enemigo e = enemigos()[0];", 1),
        new Case(ErrorCode.FOR_EACH_NOT_A_LIST, "for (Enemigo e : enemigoMasCercano()) {\n}", 1),
        new Case(ErrorCode.FOR_EACH_WRONG_TYPE, "for (int x : enemigos()) {\n}", 1),
        new Case(ErrorCode.FEATURE_LOCKED, "try {\n  disparar();\n} catch (Exception e) {\n}", 1),
        new Case(ErrorCode.FEATURE_LOCKED, "void ayuda() {\n  disparar();\n}", 1),
        new Case(ErrorCode.FEATURE_LOCKED, "public class Torreta {\n}", 1),
        new Case(ErrorCode.FEATURE_LOCKED, "while (hayEnemigos()) {\n  disparar();\n}", 1, NOTHING),
        new Case(ErrorCode.API_LOCKED_FEATURE, "municion();", 1, NOTHING),
        new Case(ErrorCode.API_LOCKED_WAVE, "usarMunicion(PERFORANTE);", 1, Unlocks.of(5)),
        new Case(ErrorCode.UNKNOWN_SYMBOL, "System.exit(0);", 1),
        new Case(ErrorCode.UNKNOWN_SYMBOL, "int x = y;", 1),
        new Case(ErrorCode.UNKNOWN_TYPE, "Torreta t = null;", 1),
        new Case(ErrorCode.UNKNOWN_METHOD, "saltar();", 1),
        new Case(ErrorCode.UNKNOWN_METHOD, "vida();", 1),
        new Case(
            ErrorCode.UNKNOWN_ENEMY_METHOD, "Enemigo e = enemigoMasCercano();\ne.saltar();", 2),
        new Case(ErrorCode.BAD_RECEIVER, "int x = 3;\nint y = x.vida();", 2),
        new Case(ErrorCode.FIELD_ACCESS, "Enemigo e = enemigoMasCercano();\nint v = e.vida;", 2),
        new Case(ErrorCode.DUPLICATE_VARIABLE, "int x = 1;\nint x = 2;", 2),
        new Case(ErrorCode.DUPLICATE_VARIABLE, "int x = 1;\nif (true) {\n  int x = 2;\n}", 3),
        new Case(ErrorCode.UNINITIALIZED_VARIABLE, "int x;\nint y = x;", 2),
        new Case(
            ErrorCode.UNINITIALIZED_VARIABLE,
            "int x;\nif (hayEnemigos()) {\n  x = 1;\n}\nint y = x;",
            5),
        new Case(ErrorCode.UNINITIALIZED_VARIABLE, "int x = x + 1;", 1),
        new Case(ErrorCode.TYPE_MISMATCH, "int x = true;", 1),
        new Case(ErrorCode.TYPE_MISMATCH, "if (3) {\n  disparar();\n}", 1),
        new Case(ErrorCode.TYPE_MISMATCH, "int x = disparar();", 1),
        new Case(ErrorCode.TYPE_MISMATCH, "Enemigo e = 3;", 1),
        new Case(ErrorCode.BAD_OPERAND_TYPES, "boolean b = 1 < true;", 1),
        new Case(ErrorCode.BAD_OPERAND_TYPES, "boolean b = hayEnemigos() && 3;", 1),
        new Case(ErrorCode.BAD_OPERAND_TYPES, "boolean b = true;\nb += 1;", 2),
        new Case(ErrorCode.BAD_OPERAND_TYPE, "int x = !3;", 1),
        new Case(ErrorCode.BAD_OPERAND_TYPE, "boolean b = true;\nb++;", 2),
        new Case(ErrorCode.WRONG_ARGUMENT_COUNT, "disparar(3);", 1),
        new Case(ErrorCode.WRONG_ARGUMENT_COUNT, "apuntar();", 1),
        new Case(ErrorCode.ARGUMENT_TYPE_MISMATCH, "apuntar(3);", 1),
        new Case(ErrorCode.ARGUMENT_TYPE_MISMATCH, "usarMunicion(3);", 1),
        new Case(ErrorCode.NOT_PRINTABLE, "imprimir(disparar());", 1),
        new Case(ErrorCode.NOT_PRINTABLE, "imprimir(NORMAL);", 1),
        new Case(ErrorCode.CANNOT_ASSIGN, "NORMAL = 3;", 1),
        new Case(ErrorCode.CANNOT_ASSIGN, "municion() = 3;", 1),
        new Case(ErrorCode.ASSIGNMENT_NOT_ALLOWED_HERE, "int x = 1;\nint y = x++;", 2),
        new Case(
            ErrorCode.ASSIGNMENT_NOT_ALLOWED_HERE,
            "int x;\nif ((x = 3) > 2) {\n  disparar();\n}",
            2),
        new Case(ErrorCode.MEMORY_EXCEEDED, "disparar();\n".repeat(13), 13),
        // Methods
        new Case(ErrorCode.FEATURE_LOCKED, "return;", 1),
        Case.library(ErrorCode.MODIFIER_NOT_ALLOWED, "static void a() {\n}", 1),
        new Case(ErrorCode.MODIFIER_NOT_ALLOWED, "final int x = 1;", 1),
        Case.library(ErrorCode.STATEMENT_OUTSIDE_METHOD, "void a() {\n}\ndisparar();", 3),
        Case.library(ErrorCode.STATEMENT_OUTSIDE_METHOD, "int x = 1;", 1),
        new Case(ErrorCode.METHOD_NOT_ALLOWED_HERE, "void a() {\n}", 1, Scripts.METHODS),
        Case.library(ErrorCode.METHOD_NOT_ALLOWED_HERE, "void a() {\n  void b() {\n  }\n}", 2),
        Case.library(ErrorCode.DUPLICATE_METHOD, "void a() {\n}\nvoid a(int x) {\n}", 3),
        Case.library(ErrorCode.METHOD_NAME_TAKEN, "void disparar() {\n}", 1),
        Case.library(
            ErrorCode.MISSING_RETURN,
            "int a() {\n  if (hayEnemigos()) {\n    return 1;\n  }\n}",
            1),
        Case.library(ErrorCode.UNEXPECTED_RETURN_VALUE, "void a() {\n  return 1;\n}", 2),
        new Case(ErrorCode.UNEXPECTED_RETURN_VALUE, "disparar();\nreturn 1;", 2, Scripts.METHODS),
        Case.library(ErrorCode.MISSING_RETURN_VALUE, "int a() {\n  return;\n}", 2),
        Case.library(ErrorCode.TYPE_MISMATCH, "int a() {\n  return true;\n}", 2),
        Case.library(ErrorCode.UNSUPPORTED_TYPE, "void a(void x) {\n}", 1),
        Case.library(ErrorCode.DUPLICATE_VARIABLE, "void a(int x, int x) {\n}", 1),
        Case.library(ErrorCode.FEATURE_LOCKED, "void a() {\n}", 1).locked(),
        Case.calling(
            ErrorCode.LIBRARY_HAS_ERRORS, "void rafaga() {\n  saltar();\n}", "rafaga();", 1),
        Case.calling(ErrorCode.WRONG_ARGUMENT_COUNT, "void a(int n) {\n}", "a();", 1),
        Case.calling(ErrorCode.ARGUMENT_TYPE_MISMATCH, "void a(int n) {\n}", "a(true);", 1),
        Case.calling(ErrorCode.TYPE_MISMATCH, "void a() {\n}", "int x = a();", 1));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void reportsErrorAtLine(Case c) {
    int memory = c.code() == ErrorCode.MEMORY_EXCEEDED ? 12 : Scripts.ROOMY_MEMORY;
    if (c.inLibrary()) {
      LibraryResult result = Scripts.COMPILER.compileLibrary(c.source(), c.unlocks());
      assertReported(c, result.errors());
      assertTrue(result.library().hasErrors(), "a library with errors must say so");
      return;
    }
    Library library =
        c.library() == null
            ? Library.EMPTY
            : Scripts.COMPILER.compileLibrary(c.library(), c.unlocks()).library();
    CompileResult result = Scripts.COMPILER.compile(c.source(), library, c.unlocks(), memory);
    assertReported(c, result.errors());
    assertTrue(result.script().isEmpty(), "a script with errors must not be runnable");
  }

  private static void assertReported(Case c, List<CompileError> errors) {
    assertTrue(
        errors.stream().anyMatch(e -> e.code() == c.code() && e.line() == c.line()),
        () ->
            "Expected " + c.code() + " at line " + c.line() + ", got " + Scripts.describe(errors));
  }

  @Test
  void everyErrorCodeHasACase() {
    Set<ErrorCode> covered =
        cases()
            .map(Case::code)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(ErrorCode.class)));
    // INTERNAL_ERROR cannot come from player code; it has its own test.
    covered.add(ErrorCode.INTERNAL_ERROR);
    assertEquals(EnumSet.allOf(ErrorCode.class), covered);
  }

  @Test
  void compilerBugBecomesInternalErrorInsteadOfThrowing() {
    CompileResult result = Scripts.COMPILER.compile(null, ALL, 12);
    assertEquals(ErrorCode.INTERNAL_ERROR, result.errors().get(0).code());
    assertTrue(result.internalError().isPresent());
    assertTrue(result.script().isEmpty());
  }

  @Test
  void featureLockedNamesTheFeature() {
    CompileError error =
        Scripts.expectError(
            "while (hayEnemigos()) {\n  disparar();\n}", NOTHING, ErrorCode.FEATURE_LOCKED, 1);
    assertEquals(List.of(Feature.WHILE), error.args());
  }

  @Test
  void errorsComeSortedAndWithColumns() {
    List<CompileError> errors = Scripts.compile("int a = true;\n  saltar();").errors();
    assertEquals(2, errors.size());
    assertEquals(ErrorCode.TYPE_MISMATCH, errors.get(0).code());
    assertEquals(9, errors.get(0).column());
    assertEquals(ErrorCode.UNKNOWN_METHOD, errors.get(1).code());
    assertEquals(3, errors.get(1).column());
  }

  @Test
  void missingSemicolonIsReportedAtTheEndOfItsLineAndTheNextLineStillCompiles() {
    List<CompileError> errors = Scripts.compile("disparar()\nsaltar();").errors();
    // Both reported: the parser kept line 2 instead of swallowing it.
    assertEquals(ErrorCode.EXPECTED_TOKEN, errors.get(0).code());
    assertEquals(1, errors.get(0).line());
    assertEquals(11, errors.get(0).column());
    assertEquals(ErrorCode.UNKNOWN_METHOD, errors.get(1).code());
    assertEquals(2, errors.get(1).line());
  }

  @Test
  void severalErrorsAreReportedInOneCompile() {
    String source = "int x = true;\nsaltar();\nint y = z;\nwhile (3) {\n}";
    List<CompileError> errors = Scripts.compile(source).errors();
    assertEquals(List.of(1, 2, 3, 4), errors.stream().map(CompileError::line).distinct().toList());
  }

  @Test
  void errorArgumentsUseTheDataDrivenTypeNames() {
    CompileError error = Scripts.expectError("Enemigo e = 3;", ErrorCode.TYPE_MISMATCH, 1);
    assertEquals(List.of("Enemigo", "int"), error.args());
  }
}
