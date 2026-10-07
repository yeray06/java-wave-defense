package io.github.yeray06.wavedefense.sim.world;

import io.github.yeray06.wavedefense.sim.data.EnemyCatalog;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;

/** A pooled enemy. Reused across spawns; {@link #generation} tells reuses apart. */
public final class Enemy {
  final int index;
  int generation;
  boolean active;
  EnemyKind kind;
  EnemyCatalog.EnemyType type;
  double maxHealth;
  double health;

  /** Distance travelled along the path. */
  double distance;

  boolean armored;
  int spawnTick;
  int lastDamageTick;
  double x;
  double y;

  /** Position one tick earlier, for the render's interpolation (D-40). */
  double prevX;

  double prevY;

  /** Path distance where it was last inside some turret's range, or -1. */
  double lastInRangeDistance;

  Enemy(int index) {
    this.index = index;
  }

  /** Script handle: generation in the high bits, slot + 1 in the low bits (never 0). */
  public long handle() {
    return ((long) generation << 32) | (index + 1L);
  }

  public boolean active() {
    return active;
  }

  public EnemyKind kind() {
    return kind;
  }

  public double health() {
    return health;
  }

  public double maxHealth() {
    return maxHealth;
  }

  public double distance() {
    return distance;
  }

  public boolean armored() {
    return armored;
  }

  public double x() {
    return x;
  }

  public double y() {
    return y;
  }

  public double prevX() {
    return prevX;
  }

  public double prevY() {
    return prevY;
  }

  public int generation() {
    return generation;
  }
}
