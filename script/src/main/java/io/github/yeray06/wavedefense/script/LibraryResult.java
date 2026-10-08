package io.github.yeray06.wavedefense.script;

import io.github.yeray06.wavedefense.script.check.Library;
import io.github.yeray06.wavedefense.script.diag.CompileError;
import java.util.List;
import java.util.Optional;

/**
 * Outcome of compiling the library.
 *
 * @param errors errors sorted by position; empty when it compiled
 * @param library what turret scripts compile against: always present, so they can name its methods;
 *     while it has errors, they are told so instead of running it
 * @param internalError cause of an {@link
 *     io.github.yeray06.wavedefense.script.diag.ErrorCode#INTERNAL_ERROR}, for the log
 */
public record LibraryResult(
    List<CompileError> errors, Library library, Optional<Throwable> internalError) {

  public LibraryResult {
    errors = List.copyOf(errors);
  }

  public boolean succeeded() {
    return errors.isEmpty();
  }
}
