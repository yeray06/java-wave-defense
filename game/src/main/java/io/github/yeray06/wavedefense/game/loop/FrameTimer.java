package io.github.yeray06.wavedefense.game.loop;

import java.util.function.LongSupplier;

/**
 * Real time between frames, measured by the game itself. The backend's delta is only refreshed by
 * its own loop, so frames drawn from window callbacks (resizing or moving the window, D-48) would
 * all reuse a stale value. Clamped: a long stall is the {@link FixedStepClock}'s to cap.
 */
public final class FrameTimer {
  private final LongSupplier nanos;
  private final float maxSeconds;
  private long last = -1;

  /**
   * @param nanos a monotonic clock in nanoseconds ({@code System::nanoTime} in the game)
   * @param maxSeconds the longest frame reported
   */
  public FrameTimer(LongSupplier nanos, float maxSeconds) {
    this.nanos = nanos;
    this.maxSeconds = maxSeconds;
  }

  /** Seconds since the previous call; 0 on the first. */
  public float next() {
    long now = nanos.getAsLong();
    if (last < 0) {
      last = now;
      return 0;
    }
    float seconds = (now - last) / 1e9f;
    last = now;
    return Math.max(0, Math.min(maxSeconds, seconds));
  }
}
