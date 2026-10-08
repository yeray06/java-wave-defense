package io.github.yeray06.wavedefense.script.runtime;

/**
 * Unwinds a method (or the turret's script) at a {@code return}. Like {@link Signal}, one instance
 * per frame is reused and it has no stack trace, so returning never allocates.
 */
final class Return extends RuntimeException {
  private static final long serialVersionUID = 1L;

  Return() {
    super(null, null, false, false);
  }
}
