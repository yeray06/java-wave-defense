package io.github.yeray06.wavedefense.script.api;

/**
 * Safety limits of the interpreter. Values come from data; none is written in code.
 *
 * @param maxLoopIterations iterations one {@code while} may run each time it is entered; the next
 *     one raises the infinite-loop exception (the plan's 1,000)
 * @param maxStepsPerCycle statements and loop iterations one cycle may execute in total, so nested
 *     loops cannot stall the game
 * @param maxNestingDepth deepest nesting of statements and expressions the parser accepts, so no
 *     script can overflow the Java stack
 */
public record ScriptLimits(int maxLoopIterations, int maxStepsPerCycle, int maxNestingDepth) {

  public ScriptLimits {
    requirePositive("maxLoopIterations", maxLoopIterations);
    requirePositive("maxStepsPerCycle", maxStepsPerCycle);
    requirePositive("maxNestingDepth", maxNestingDepth);
  }

  private static void requirePositive(String name, int value) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive: " + value);
    }
  }
}
