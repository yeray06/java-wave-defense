package io.github.yeray06.wavedefense.sim.rng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SplitMix64Test {

  @Test
  void matchesReferenceOutputForSeedZero() {
    SplitMix64 rng = new SplitMix64(0L);
    assertEquals(0xE220A8397B1DCDAFL, rng.nextLong());
    assertEquals(0x6E789E6AA1B965F4L, rng.nextLong());
    assertEquals(0x06C45D188009454FL, rng.nextLong());
  }

  @Test
  void sameSeedGivesSameSequence() {
    SplitMix64 a = new SplitMix64(42L);
    SplitMix64 b = new SplitMix64(42L);
    for (int i = 0; i < 1_000; i++) {
      assertEquals(a.nextLong(), b.nextLong());
    }
  }

  @Test
  void nextIntStaysInRange() {
    SplitMix64 rng = new SplitMix64(7L);
    for (int i = 0; i < 10_000; i++) {
      int value = rng.nextInt(13);
      assertTrue(value >= 0 && value < 13, "out of range: " + value);
    }
  }

  @Test
  void nextDoubleStaysInUnitInterval() {
    SplitMix64 rng = new SplitMix64(7L);
    for (int i = 0; i < 10_000; i++) {
      double value = rng.nextDouble();
      assertTrue(value >= 0.0 && value < 1.0, "out of range: " + value);
    }
  }

  @Test
  void rejectsNonPositiveBound() {
    assertThrows(IllegalArgumentException.class, () -> new SplitMix64(1L).nextInt(0));
  }
}
