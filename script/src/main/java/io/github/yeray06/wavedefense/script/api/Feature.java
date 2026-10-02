package io.github.yeray06.wavedefense.script.api;

/**
 * Pieces of Java syntax sold in the shop, in the plan's order. The MVP sells the first four; the
 * rest exist so the parser can report them as locked instead of as syntax errors.
 */
public enum Feature {
  IF_ELSE,
  VARIABLES,
  LOGIC_OPS,
  WHILE,
  FOR,
  METHODS,
  TRY_CATCH,
  CLASSES
}
