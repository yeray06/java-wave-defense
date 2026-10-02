package io.github.yeray06.wavedefense.script.check;

/** Static types of the checker: the game's value types plus {@code null}, void and error. */
enum Type {
  INT,
  BOOLEAN,
  ENEMY,
  AMMO,
  NULL,
  VOID,
  /** Already reported; anything combined with it stays silent to avoid cascades. */
  ERROR;

  boolean isReference() {
    return this == ENEMY || this == NULL;
  }
}
