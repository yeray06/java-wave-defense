package io.github.yeray06.wavedefense.script.runtime;

/** An executable {@code boolean} expression. */
@FunctionalInterface
public interface BoolEval {
  boolean eval(Frame frame);
}
