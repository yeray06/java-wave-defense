package io.github.yeray06.wavedefense.script.diag;

import java.util.List;

/**
 * A compile error at a 1-based line and column. Arguments are {@link String}, {@link Integer} or
 * {@link io.github.yeray06.wavedefense.script.api.Feature}; the UI localizes features by name.
 */
public record CompileError(ErrorCode code, int line, int column, List<Object> args) {

  public CompileError {
    args = List.copyOf(args);
  }
}
