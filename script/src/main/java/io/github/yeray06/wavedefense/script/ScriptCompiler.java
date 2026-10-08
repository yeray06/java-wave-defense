package io.github.yeray06.wavedefense.script;

import io.github.yeray06.wavedefense.script.api.ScriptApi;
import io.github.yeray06.wavedefense.script.api.ScriptLimits;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.script.ast.Stmt;
import io.github.yeray06.wavedefense.script.check.Checker;
import io.github.yeray06.wavedefense.script.check.Library;
import io.github.yeray06.wavedefense.script.diag.CompileError;
import io.github.yeray06.wavedefense.script.diag.Diagnostics;
import io.github.yeray06.wavedefense.script.diag.ErrorCode;
import io.github.yeray06.wavedefense.script.lexer.Lexer;
import io.github.yeray06.wavedefense.script.lexer.Token;
import io.github.yeray06.wavedefense.script.parser.Parser;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Entry point of the interpreter: source text in, runnable script or errors out. Stateless and safe
 * to share. Never throws for any input.
 */
public final class ScriptCompiler {
  private final ScriptApi api;
  private final ScriptLimits limits;

  public ScriptCompiler(ScriptApi api, ScriptLimits limits) {
    this.api = api;
    this.limits = limits;
  }

  /** A turret's script without a library. */
  public CompileResult compile(String source, Unlocks unlocks, int memoryLimit) {
    return compile(source, Library.EMPTY, unlocks, memoryLimit);
  }

  /**
   * A turret's script, which may call the methods of {@code library}.
   *
   * @param memoryLimit memory lines the turret has (base plus hardware)
   */
  public CompileResult compile(String source, Library library, Unlocks unlocks, int memoryLimit) {
    Diagnostics diagnostics = new Diagnostics();
    try {
      List<Token> tokens = Lexer.tokenize(source, diagnostics);
      List<Stmt> statements = Parser.parse(tokens, diagnostics, limits.maxNestingDepth());
      Checker.Result checked =
          Checker.check(statements, library, api, unlocks, memoryLimit, diagnostics);
      // Room for the script plus the deepest chain of library calls allowed.
      int intSlots = checked.intSlots() + limits.maxCallDepth() * library.maxIntSlots();
      int refSlots = checked.refSlots() + limits.maxCallDepth() * library.maxRefSlots();
      Optional<CompiledScript> script =
          diagnostics.hasErrors()
              ? Optional.empty()
              : Optional.of(
                  new CompiledScript(
                      checked.root(),
                      checked.script(),
                      intSlots,
                      refSlots,
                      Math.max(checked.intSlots(), library.maxIntSlots()),
                      Math.max(checked.refSlots(), library.maxRefSlots()),
                      limits));
      return new CompileResult(
          diagnostics.sorted(),
          script,
          checked.memoryUsed(),
          memoryLimit,
          checked.memoryByLine(),
          checked.debugVars(),
          Optional.empty());
    } catch (RuntimeException | StackOverflowError e) {
      return new CompileResult(
          List.of(new CompileError(ErrorCode.INTERNAL_ERROR, 1, 1, List.of())),
          Optional.empty(),
          0,
          memoryLimit,
          new TreeMap<>(),
          List.of(),
          Optional.of(e));
    }
  }

  /** The library: methods shared by every turret, which take no memory. */
  public LibraryResult compileLibrary(String source, Unlocks unlocks) {
    Diagnostics diagnostics = new Diagnostics();
    try {
      List<Token> tokens = Lexer.tokenize(source, diagnostics);
      List<Stmt.MethodDecl> methods =
          Parser.parseLibrary(tokens, diagnostics, limits.maxNestingDepth());
      Library library = Checker.checkLibrary(methods, api, unlocks, diagnostics);
      return new LibraryResult(diagnostics.sorted(), library, Optional.empty());
    } catch (RuntimeException | StackOverflowError e) {
      return new LibraryResult(
          List.of(new CompileError(ErrorCode.INTERNAL_ERROR, 1, 1, List.of())),
          Library.BROKEN,
          Optional.of(e));
    }
  }
}
