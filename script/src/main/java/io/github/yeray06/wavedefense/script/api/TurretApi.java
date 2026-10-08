package io.github.yeray06.wavedefense.script.api;

/**
 * The only thing a script can touch. Implemented by the simulation. Implementations must not throw:
 * anything they throw is caught and reported as an internal error that hangs the turret.
 *
 * <p>Enemy handles passed in are always ones the interpreter checked with {@link #exists(long)}.
 */
public interface TurretApi {

  FireResult fire();

  /** Starts a reload. The interpreter ends the script right after this call. */
  void reload();

  int ammo();

  int heat();

  boolean hasEnemies();

  /** Nearest living enemy in range, or {@link EnemyHandle#NONE}. */
  long nearestEnemy();

  /**
   * How many living enemies are in range. Within one script run the answer does not change: the
   * world only moves between turret cycles.
   */
  int enemyCount();

  /**
   * The {@code index}-th enemy in range, in the order they came out. The interpreter only calls
   * this with {@code 0 <= index < enemyCount()}.
   */
  long enemyAt(int index);

  /** Aimed enemy if it is still alive and in range, otherwise {@link EnemyHandle#NONE}. */
  long target();

  void aim(long enemy);

  void useAmmo(AmmoType type);

  /** Whether the handle still refers to a living enemy. Never called with {@code NONE}. */
  boolean exists(long enemy);

  int health(long enemy);

  int speed(long enemy);

  boolean armored(long enemy);

  int distanceToCore(long enemy);
}
