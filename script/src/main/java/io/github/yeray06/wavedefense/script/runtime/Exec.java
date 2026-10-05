package io.github.yeray06.wavedefense.script.runtime;

/** An executable statement. */
@FunctionalInterface
public interface Exec {
  void exec(Frame frame);
}
