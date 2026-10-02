package io.github.yeray06.wavedefense.script.runtime;

/** An executable {@code int} expression. */
@FunctionalInterface
public interface IntEval {
  int eval(Frame frame);
}
