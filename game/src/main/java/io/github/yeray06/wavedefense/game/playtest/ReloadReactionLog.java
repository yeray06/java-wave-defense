package io.github.yeray06.wavedefense.game.playtest;

import io.github.yeray06.wavedefense.sim.world.Battle;
import io.github.yeray06.wavedefense.sim.world.Turret;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Yeray's decision C: how long the player takes to reload by hand once a turret runs dry. Each
 * manual reload of an empty turret is logged with the real seconds since it emptied; when the
 * logged wave ends, a summary follows. Only measures, never changes the game.
 */
public final class ReloadReactionLog {
  private final int wave;
  private final Consumer<String> out;
  private final double[] emptySince;
  private Battle battle;
  private int samples;
  private double total;
  private double min = Double.MAX_VALUE;
  private double max;

  /**
   * @param wave the wave to measure (decision C: wave 1)
   * @param slots turret slots on the map
   * @param out where lines go (the game's log)
   */
  public ReloadReactionLog(int wave, int slots, Consumer<String> out) {
    this.wave = wave;
    this.out = out;
    this.emptySince = new double[slots];
    java.util.Arrays.fill(emptySince, -1);
  }

  /** After each tick: notes when a turret has just run dry. */
  public void afterTick(Battle current, double nowSeconds) {
    if (current != battle) {
      battle = current;
      java.util.Arrays.fill(emptySince, -1);
    }
    if (current.wave() != wave) {
      return;
    }
    for (int i = 0; i < current.turretCount(); i++) {
      Turret turret = current.turret(i);
      if (turret.ammo() > 0 || turret.reloading()) {
        emptySince[i] = -1;
      } else if (emptySince[i] < 0) {
        emptySince[i] = nowSeconds;
      }
    }
  }

  /** The player's click started a manual reload on {@code turret}. */
  public void manualReload(Battle current, int turret, double nowSeconds) {
    if (current != battle || current.wave() != wave || emptySince[turret] < 0) {
      return;
    }
    double seconds = nowSeconds - emptySince[turret];
    emptySince[turret] = -1;
    samples++;
    total += seconds;
    min = Math.min(min, seconds);
    max = Math.max(max, seconds);
    out.accept(
        String.format(
            Locale.ROOT,
            "manual reload, wave %d, turret %d: %.2f s after the magazine ran dry",
            wave,
            turret + 1,
            seconds));
  }

  /** A wave ended: logs the summary if it was the measured wave and resets for the next try. */
  public void waveFinished(int finishedWave) {
    if (finishedWave != wave) {
      return;
    }
    if (samples == 0) {
      out.accept(String.format(Locale.ROOT, "wave %d: no manual reloads of an empty turret", wave));
    } else {
      out.accept(
          String.format(
              Locale.ROOT,
              "wave %d manual reloads: %d, mean %.2f s, min %.2f s, max %.2f s",
              wave,
              samples,
              total / samples,
              min,
              max));
    }
    samples = 0;
    total = 0;
    min = Double.MAX_VALUE;
    max = 0;
  }
}
