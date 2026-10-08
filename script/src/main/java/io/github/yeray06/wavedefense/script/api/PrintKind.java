package io.github.yeray06.wavedefense.script.api;

/** What {@code imprimir(x)} printed, so the game can write it the way the player wrote it. */
public enum PrintKind {
  /** An {@code int}: the value is the number. */
  INT,
  /** A {@code boolean}: the value is 1 for true and 0 for false. */
  BOOLEAN,
  /** An enemy: the value is its handle. */
  ENEMY,
  /** {@code null}. */
  NULL,
  /** A list of enemies: the value is how many it holds. */
  LIST
}
