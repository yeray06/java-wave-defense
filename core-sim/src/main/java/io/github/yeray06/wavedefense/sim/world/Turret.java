package io.github.yeray06.wavedefense.sim.world;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.api.EnemyHandle;
import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;

/** One built turret: its script, its state and its counters for the report. */
public final class Turret {
  final int index;
  final double x;
  final double y;
  final CompiledScript script;
  final FaultLog faults = new FaultLog();

  int ammo;
  int heat;

  /** Cycles left before a reload finishes; 0 when not reloading. */
  int reloadCyclesLeft;

  int hungTicksLeft;
  long aimed = EnemyHandle.NONE;
  AmmoType ammoType = AmmoType.NORMAL;

  FaultType lastFault;
  int lastFaultLine;
  boolean lastFaultInLibrary;

  int shots;
  int airShots;
  int wastedOnDead;
  int reloads;
  int manualReloads;
  int hungTicks;

  Turret(int index, double x, double y, CompiledScript script, int magazine) {
    this.index = index;
    this.x = x;
    this.y = y;
    this.script = script;
    this.ammo = magazine;
  }

  public int index() {
    return index;
  }

  public double x() {
    return x;
  }

  public double y() {
    return y;
  }

  public int ammo() {
    return ammo;
  }

  public int heat() {
    return heat;
  }

  public boolean reloading() {
    return reloadCyclesLeft > 0;
  }

  public boolean hung() {
    return hungTicksLeft > 0;
  }

  public AmmoType ammoType() {
    return ammoType;
  }

  /** The exception that hung the turret most recently, or {@code null}: shown above it. */
  public FaultType lastFault() {
    return lastFault;
  }

  public int lastFaultLine() {
    return lastFaultLine;
  }

  /** Whether {@link #lastFaultLine()} is a line of the library rather than of the turret's code. */
  public boolean lastFaultInLibrary() {
    return lastFaultInLibrary;
  }

  /** Whether a script is loaded; a turret whose code does not compile stands idle. */
  public boolean hasScript() {
    return script != null;
  }

  WaveReport.TurretReport report() {
    return new WaveReport.TurretReport(
        shots, airShots, wastedOnDead, reloads, manualReloads, hungTicks, faults.snapshot());
  }
}
