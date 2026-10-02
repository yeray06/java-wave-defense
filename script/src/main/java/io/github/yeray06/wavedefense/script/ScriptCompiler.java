package io.github.yeray06.wavedefense.script;

import io.github.yeray06.wavedefense.script.api.ScriptApi;
import io.github.yeray06.wavedefense.script.api.ScriptLimits;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.script.ast.Stmt;
import io.github.yeray06.wavedefense.script.check.Checker;
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

  /**
   * @param memoryLimit memory lines the turret has (base plus hardware)
   */
  public CompileResult compile(String source, Unlocks unlocks, int memoryLimit) {
    Diagnostics diagnostics = new Diagnostics();
    try {
      List<Token> tokens = Lexer.tokenize(source, diagnostics);
      List<Stmt> statements = Parser.parse(tokens, diagnostics, limits.maxNestingDepth());
      Checker.Result checked = Checker.check(statements, api, unlocks, memoryLimit, diagnostics);
      Optional<CompiledScript> script =
          diagnostics.hasErrors()
              ? Optional.empty()
              : Optional.of(
                  new CompiledScript(
                      checked.root(), checked.intSlots(), checked.refSlots(), limits));
      return new CompileResult(
          diagnostics.sorted(),
          script,
          checked.memoryUsed(),
          memoryLimit,
          checked.memoryByLine(),
          Optional.empty());
    } catch (RuntimeException | StackOverflowError e) {
      return new CompileResult(
          List.of(new CompileError(ErrorCode.INTERNAL_ERROR, 1, 1, List.of())),
          Optional.empty(),
          0,
          memoryLimit,
          new TreeMap<>(),
          Optional.of(e));
    }
  }
}
