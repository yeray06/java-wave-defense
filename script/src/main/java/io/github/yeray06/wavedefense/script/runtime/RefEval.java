package io.github.yeray06.wavedefense.script.runtime;

/** An executable enemy expression; yields a handle, {@code NONE} for {@code null}. */
@FunctionalInterface
public interface RefEval {
  long eval(Frame frame);
}
