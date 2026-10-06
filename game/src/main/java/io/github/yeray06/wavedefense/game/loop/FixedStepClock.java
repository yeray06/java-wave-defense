package io.github.yeray06.wavedefense.game.loop;

/**
 * Turns frame time into whole simulation ticks (ARCHITECTURE §5). The tick length never changes: ×3
 * runs three times as many ticks per frame, never a longer step. A frame that comes late runs at
 * most {@code maxTicksPerFrame} ticks per speed unit and drops the rest, so a stall cannot
 * snowball.
 */
public final class FixedStepClock {
  private final double tickSeconds;
  private final int maxTicksPerFrame;
  private double accumulator;

  public FixedStepClock(int ticksPerSecond, int maxTicksPerFrame) {
    if (ticksPerSecond <= 0 || maxTicksPerFrame <= 0) {
      throw new IllegalArgumentException("ticks per second and per frame must be positive");
    }
    this.tickSeconds = 1.0 / ticksPerSecond;
    this.maxTicksPerFrame = maxTicksPerFrame;
  }

  /**
   * Adds a frame's time and returns how many ticks to run now.
   *
   * @param speed 1 for normal speed, 3 for ×3
   */
  public int advance(float deltaSeconds, int speed) {
    if (speed <= 0) {
      throw new IllegalArgumentException("speed must be positive");
    }
    accumulator += Math.max(0, deltaSeconds) * speed;
    int ticks = (int) (accumulator / tickSeconds);
    int limit = maxTicksPerFrame * speed;
    if (ticks > limit) {
      ticks = limit;
      accumulator = 0;
    } else {
      accumulator -= ticks * tickSeconds;
    }
    return ticks;
  }

  /** How far into the next tick the frame is, in [0, 1): the render's interpolation factor. */
  public float alpha() {
    return (float) Math.min(1, accumulator / tickSeconds);
  }

  /** Forgets leftover time, e.g. while no wave runs. */
  public void reset() {
    accumulator = 0;
  }
}
