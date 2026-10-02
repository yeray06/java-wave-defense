package io.github.yeray06.wavedefense.script.runtime;

import io.github.yeray06.wavedefense.script.diag.FaultType;

/**
 * Unwinds a running script on a game exception or a reload. One instance per frame is reused, and
 * it has no stack trace, so raising it never allocates. It never leaves {@link CompiledScript}.
 */
final class Signal extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /** {@code null} means the script stopped at a reload. */
  FaultType fault;

  int line;

  Signal() {
    super(null, null, false, false);
  }
}
