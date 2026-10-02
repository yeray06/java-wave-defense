package io.github.yeray06.wavedefense.sim.rng;

/**
 * Seeded pseudo-random generator for the simulation.
 *
 * <p>SplitMix64 is used instead of {@link java.util.Random} because its output is fixed by this
 * code, not by the JDK, so a seed replays identically on every machine and Java version.
 */
public final class SplitMix64 {
  private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

  private long state;

  public SplitMix64(long seed) {
    this.state = seed;
  }

  public long nextLong() {
    long z = (state += GOLDEN_GAMMA);
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }

  /** Uniform integer in {@code [0, bound)}; {@code bound} must be positive. */
  public int nextInt(int bound) {
    if (bound <= 0) {
      throw new IllegalArgumentException("bound must be positive: " + bound);
    }
    // Lemire's multiply-shift on the top 32 bits, with rejection to remove bias.
    long threshold = (1L << 32) % bound;
    while (true) {
      long r = nextLong() >>> 32;
      long m = r * bound;
      if ((m & 0xFFFFFFFFL) >= threshold) {
        return (int) (m >>> 32);
      }
    }
  }

  /** Uniform double in {@code [0, 1)} with 53 bits of precision. */
  public double nextDouble() {
    return (nextLong() >>> 11) * 0x1.0p-53;
  }
}
