package io.github.yeray06.wavedefense.sim.world;

/** Fixed-capacity enemy storage, sized from data before the wave starts. */
public final class EnemyPool {
  private final Enemy[] enemies;
  private int activeCount;

  public EnemyPool(int capacity) {
    enemies = new Enemy[capacity];
    for (int i = 0; i < capacity; i++) {
      enemies[i] = new Enemy(i);
    }
  }

  public int capacity() {
    return enemies.length;
  }

  public Enemy get(int index) {
    return enemies[index];
  }

  public int activeCount() {
    return activeCount;
  }

  /** Activates a free enemy; the capacity is computed so this cannot run out. */
  Enemy obtain() {
    for (Enemy enemy : enemies) {
      if (!enemy.active) {
        enemy.active = true;
        enemy.generation++;
        activeCount++;
        return enemy;
      }
    }
    throw new IllegalStateException("enemy pool exhausted: capacity " + enemies.length);
  }

  void free(Enemy enemy) {
    enemy.active = false;
    activeCount--;
  }

  /** The living enemy a script handle refers to, or {@code null}. */
  Enemy resolve(long handle) {
    int slot = (int) (handle & 0xFFFF_FFFFL) - 1;
    if (slot < 0 || slot >= enemies.length) {
      return null;
    }
    Enemy enemy = enemies[slot];
    return enemy.active && enemy.generation == (int) (handle >>> 32) ? enemy : null;
  }
}
