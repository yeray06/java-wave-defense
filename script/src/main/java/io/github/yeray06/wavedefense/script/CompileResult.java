package io.github.yeray06.wavedefense.script;

import io.github.yeray06.wavedefense.script.diag.CompileError;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;

/**
 * Outcome of compiling a script.
 *
 * @param errors errors sorted by position; empty when the script compiled
 * @param script the runnable script, present only when there are no errors
 * @param memoryUsed memory lines the script uses (counted even when it has errors, for the editor's
 *     live counter)
 * @param memoryLimit memory lines available
 * @param memoryByLine memory lines per source line, for the editor's margin
 * @param internalError cause of an {@link
 *     io.github.yeray06.wavedefense.script.diag.ErrorCode#INTERNAL_ERROR}, for the log
 */
public record CompileResult(
    List<CompileError> errors,
    Optional<CompiledScript> script,
    int memoryUsed,
    int memoryLimit,
    SortedMap<Integer, Integer> memoryByLine,
    Optional<Throwable> internalError) {

  public CompileResult {
    errors = List.copyOf(errors);
  }

  public boolean succeeded() {
    return script.isPresent();
  }
}
