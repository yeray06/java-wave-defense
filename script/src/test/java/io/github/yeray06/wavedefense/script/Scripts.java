package io.github.yeray06.wavedefense.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.script.check.Library;
import io.github.yeray06.wavedefense.script.diag.CompileError;
import io.github.yeray06.wavedefense.script.diag.ErrorCode;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import java.util.List;
import java.util.stream.Collectors;

/** Compile helpers for tests. */
final class Scripts {
  /** Every piece the game runs, and a wave past every wave requirement. */
  static final Unlocks ALL =
      Unlocks.of(
          10, Feature.IF_ELSE, Feature.VARIABLES, Feature.LOGIC_OPS, Feature.WHILE, Feature.FOR);

  /** Every piece up to methods. */
  static final Unlocks METHODS =
      Unlocks.of(
          10,
          Feature.IF_ELSE,
          Feature.VARIABLES,
          Feature.LOGIC_OPS,
          Feature.WHILE,
          Feature.FOR,
          Feature.METHODS);

  /** The MVP's four pieces: {@code for} still locked. */
  static final Unlocks MVP =
      Unlocks.of(10, Feature.IF_ELSE, Feature.VARIABLES, Feature.LOGIC_OPS, Feature.WHILE);

  static final Unlocks NOTHING = Unlocks.of(1);

  /** Large enough that memory never gets in the way unless a test is about memory. */
  static final int ROOMY_MEMORY = 1_000;

  static final ScriptCompiler COMPILER = new ScriptCompiler(TestApi.spanish(), TestApi.LIMITS);

  private Scripts() {}

  static CompileResult compile(String source) {
    return COMPILER.compile(source, ALL, ROOMY_MEMORY);
  }

  static CompileResult compile(String source, Unlocks unlocks) {
    return COMPILER.compile(source, unlocks, ROOMY_MEMORY);
  }

  /** Compiles a library with methods unlocked. */
  static LibraryResult library(String source) {
    return COMPILER.compileLibrary(source, METHODS);
  }

  /** Compiles a library that must have no errors. */
  static Library okLibrary(String source) {
    LibraryResult result = library(source);
    if (!result.succeeded()) {
      fail(
          "Expected the library to compile:\n"
              + source
              + "\nbut got: "
              + describe(result.errors()));
    }
    return result.library();
  }

  /** A turret's script calling {@code library}, with methods unlocked. */
  static CompileResult compile(String source, Library library) {
    return COMPILER.compile(source, library, METHODS, ROOMY_MEMORY);
  }

  /** Compiles a script against a library, both of which must have no errors. */
  static CompiledScript ok(String source, String library) {
    CompileResult result = compile(source, okLibrary(library));
    if (!result.succeeded()) {
      fail("Expected to compile:\n" + source + "\nbut got: " + describe(result.errors()));
    }
    return result.script().orElseThrow();
  }

  /** Compiles with everything unlocked and fails the test on any error. */
  static CompiledScript ok(String source) {
    CompileResult result = compile(source);
    if (!result.succeeded()) {
      fail("Expected to compile:\n" + source + "\nbut got: " + describe(result.errors()));
    }
    return result.script().orElseThrow();
  }

  /** Asserts that compiling reports {@code code} at {@code line}. */
  static CompileError expectError(String source, Unlocks unlocks, ErrorCode code, int line) {
    List<CompileError> errors = compile(source, unlocks).errors();
    return errors.stream()
        .filter(e -> e.code() == code && e.line() == line)
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "Expected "
                        + code
                        + " at line "
                        + line
                        + " for:\n"
                        + source
                        + "\nbut got: "
                        + describe(errors)));
  }

  static CompileError expectError(String source, ErrorCode code, int line) {
    return expectError(source, ALL, code, line);
  }

  static void expectOnly(String source, Unlocks unlocks, ErrorCode code, int line) {
    List<CompileError> errors = compile(source, unlocks).errors();
    assertEquals(1, errors.size(), () -> "Expected only " + code + " but got " + describe(errors));
    assertEquals(code, errors.get(0).code());
    assertEquals(line, errors.get(0).line());
    assertTrue(compile(source, unlocks).script().isEmpty());
  }

  /** Asserts that a script calling {@code library} reports only {@code code} at {@code line}. */
  static void expectOnlyWith(String source, Library library, ErrorCode code, int line) {
    List<CompileError> errors = compile(source, library).errors();
    assertEquals(1, errors.size(), () -> "Expected only " + code + " but got " + describe(errors));
    assertEquals(code, errors.get(0).code());
    assertEquals(line, errors.get(0).line());
  }

  static String describe(List<CompileError> errors) {
    return errors.stream()
        .map(e -> e.code() + "@" + e.line() + ":" + e.column() + e.args())
        .collect(Collectors.joining(", ", "[", "]"));
  }
}
