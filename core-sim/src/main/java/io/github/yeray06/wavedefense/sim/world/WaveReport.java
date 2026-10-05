package io.github.yeray06.wavedefense.sim.world;

import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import java.util.List;

/**
 * What happened in one wave: the data for the after-wave report (plan, "Informe" and "Derrota").
 *
 * @param earnedCents money from kills only; bonuses are added by the progression rules
 * @param ticks how long the wave lasted, in simulation ticks
 */
public record WaveReport(
    int wave,
    boolean won,
    int spawned,
    int kills,
    List<Leak> leaks,
    boolean bossKilled,
    boolean bossLeaked,
    long earnedCents,
    int ticks,
    List<TurretReport> turrets) {

  public WaveReport {
    leaks = List.copyOf(leaks);
    turrets = List.copyOf(turrets);
  }

  /** No enemy got through ("oleada perfecta"). */
  public boolean perfect() {
    return won && leaks.isEmpty();
  }

  /**
   * An enemy that reached the core.
   *
   * @param lastDefendedDistance path distance where it last was inside a turret's range, or -1 if
   *     it never was: where on the path it slipped through
   */
  public record Leak(EnemyKind kind, double healthFraction, double lastDefendedDistance) {}

  public record FaultCount(FaultType type, int line, int count) {}

  /**
   * @param airShots shots fired with no enemy in range
   * @param wastedOnDead bullets whose target died before they arrived
   * @param hungTicks ticks spent hung after exceptions
   */
  public record TurretReport(
      int shots,
      int airShots,
      int wastedOnDead,
      int reloads,
      int hungTicks,
      List<FaultCount> faults) {
    public TurretReport {
      faults = List.copyOf(faults);
    }

    public int faultCount() {
      return faults.stream().mapToInt(FaultCount::count).sum();
    }
  }
}
