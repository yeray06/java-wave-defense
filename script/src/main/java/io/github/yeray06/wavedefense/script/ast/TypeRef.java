package io.github.yeray06.wavedefense.script.ast;

/** A type as written in a declaration. */
public sealed interface TypeRef {
  Pos pos();

  record IntType(Pos pos) implements TypeRef {}

  record BooleanType(Pos pos) implements TypeRef {}

  /** A type written by name, such as the enemy type. Resolved by the checker. */
  record Named(Pos pos, String name) implements TypeRef {}

  /** A type the parser already reported as unsupported; the checker stays silent about it. */
  record Invalid(Pos pos) implements TypeRef {}
}
