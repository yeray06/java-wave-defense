package io.github.yeray06.wavedefense.script.api;

/**
 * Enemies are passed to scripts as opaque {@code long} handles so that reading them never
 * allocates. The simulation decides the encoding (e.g. pool index plus generation); the only value
 * the interpreter knows is {@link #NONE}, which is the script's {@code null}.
 */
public final class EnemyHandle {
  public static final long NONE = 0L;

  private EnemyHandle() {}
}
