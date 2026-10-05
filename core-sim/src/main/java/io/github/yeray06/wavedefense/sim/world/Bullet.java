package io.github.yeray06.wavedefense.sim.world;

import io.github.yeray06.wavedefense.script.api.AmmoType;

/** A pooled homing bullet. */
public final class Bullet {
  boolean active;
  double x;
  double y;
  int targetIndex;
  int targetGeneration;
  double damage;
  AmmoType ammo;
  int turret;

  public boolean active() {
    return active;
  }

  public double x() {
    return x;
  }

  public double y() {
    return y;
  }

  public AmmoType ammo() {
    return ammo;
  }
}
