package io.github.yeray06.wavedefense.script;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.api.EnemyHandle;
import io.github.yeray06.wavedefense.script.api.FireResult;
import io.github.yeray06.wavedefense.script.api.TurretApi;

/**
 * Turret double with public state. Allocation-free so it can be used in the allocation test.
 * Enemies live in slots 1..{@link #MAX_ENEMIES}; their handle is the slot number.
 */
final class FakeTurret implements TurretApi {
  static final int MAX_ENEMIES = 8;
  static final int HEAT_PER_SHOT = 20;
  static final int MAX_HEAT = 100;

  int ammo = 12;
  int heat;
  int heatPerShot = HEAT_PER_SHOT;
  long nearest = EnemyHandle.NONE;
  long target = EnemyHandle.NONE;
  AmmoType ammoType = AmmoType.NORMAL;
  RuntimeException failure;

  int fireCalls;
  int shotsFired;
  int reloadCalls;
  int hasEnemiesCalls;
  long lastAimed = EnemyHandle.NONE;

  final boolean[] alive = new boolean[MAX_ENEMIES + 1];
  final int[] health = new int[MAX_ENEMIES + 1];
  final int[] speed = new int[MAX_ENEMIES + 1];
  final boolean[] armored = new boolean[MAX_ENEMIES + 1];
  final int[] distance = new int[MAX_ENEMIES + 1];

  /** Adds a living enemy and returns its handle. */
  long addEnemy(int slot, int hp, int spd, boolean isArmored, int toCore) {
    alive[slot] = true;
    health[slot] = hp;
    speed[slot] = spd;
    armored[slot] = isArmored;
    distance[slot] = toCore;
    return slot;
  }

  void kill(long handle) {
    alive[(int) handle] = false;
  }

  @Override
  public FireResult fire() {
    fail();
    fireCalls++;
    if (ammo == 0) {
      return FireResult.NO_AMMO;
    }
    if (heat >= MAX_HEAT) {
      return FireResult.OVERHEATED;
    }
    ammo--;
    heat = Math.min(MAX_HEAT, heat + heatPerShot);
    shotsFired++;
    return FireResult.FIRED;
  }

  @Override
  public void reload() {
    fail();
    reloadCalls++;
  }

  @Override
  public int ammo() {
    fail();
    return ammo;
  }

  @Override
  public int heat() {
    return heat;
  }

  @Override
  public boolean hasEnemies() {
    hasEnemiesCalls++;
    for (boolean living : alive) {
      if (living) {
        return true;
      }
    }
    return false;
  }

  /** Living enemies in slot order: the fake's "order they came out". */
  @Override
  public int enemyCount() {
    int count = 0;
    for (int slot = 1; slot <= MAX_ENEMIES; slot++) {
      if (alive[slot]) {
        count++;
      }
    }
    return count;
  }

  @Override
  public long enemyAt(int index) {
    int seen = 0;
    for (int slot = 1; slot <= MAX_ENEMIES; slot++) {
      if (alive[slot] && seen++ == index) {
        return slot;
      }
    }
    throw new IllegalArgumentException("no enemy " + index);
  }

  @Override
  public long nearestEnemy() {
    return nearest;
  }

  @Override
  public long target() {
    return target;
  }

  @Override
  public void aim(long enemy) {
    lastAimed = enemy;
    target = enemy;
  }

  @Override
  public void useAmmo(AmmoType type) {
    ammoType = type;
  }

  @Override
  public boolean exists(long enemy) {
    return enemy > 0 && enemy <= MAX_ENEMIES && alive[(int) enemy];
  }

  @Override
  public int health(long enemy) {
    return health[(int) enemy];
  }

  @Override
  public int speed(long enemy) {
    return speed[(int) enemy];
  }

  @Override
  public boolean armored(long enemy) {
    return armored[(int) enemy];
  }

  @Override
  public int distanceToCore(long enemy) {
    return distance[(int) enemy];
  }

  private void fail() {
    if (failure != null) {
      throw failure;
    }
  }
}
