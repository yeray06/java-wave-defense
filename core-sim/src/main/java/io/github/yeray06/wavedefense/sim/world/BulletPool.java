package io.github.yeray06.wavedefense.sim.world;

/** Fixed-capacity bullet storage. */
public final class BulletPool {
  private final Bullet[] bullets;

  public BulletPool(int capacity) {
    bullets = new Bullet[capacity];
    for (int i = 0; i < capacity; i++) {
      bullets[i] = new Bullet();
    }
  }

  public int capacity() {
    return bullets.length;
  }

  public Bullet get(int index) {
    return bullets[index];
  }

  /** A free bullet, or {@code null} if every slot is in flight. */
  Bullet obtain() {
    for (Bullet bullet : bullets) {
      if (!bullet.active) {
        bullet.active = true;
        return bullet;
      }
    }
    return null;
  }
}
