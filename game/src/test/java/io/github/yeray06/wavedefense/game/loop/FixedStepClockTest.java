package io.github.yeray06.wavedefense.game.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FixedStepClockTest {
  @Test
  void oneSecondAtSixtyFramesRunsSixtyTicksAndTripleAtSpeedThree() {
    FixedStepClock clock = new FixedStepClock(60, 5);
    int normal = 0;
    int fast = 0;
    for (int frame = 0; frame < 60; frame++) {
      normal += clock.advance(1f / 60, 1);
    }
    clock.reset();
    for (int frame = 0; frame < 60; frame++) {
      fast += clock.advance(1f / 60, 3);
    }
    assertEquals(60, normal, 1);
    assertEquals(180, fast, 1);
  }

  @Test
  void slowFramesCarryTheRemainderAndAlphaStaysInRange() {
    FixedStepClock clock = new FixedStepClock(60, 5);
    int ticks = 0;
    for (int frame = 0; frame < 144; frame++) {
      ticks += clock.advance(1f / 144, 1);
      assertTrue(clock.alpha() >= 0 && clock.alpha() < 1);
    }
    assertEquals(60, ticks, 1);
  }

  @Test
  void aStalledFrameRunsAtMostTheLimitAndDropsTheRest() {
    FixedStepClock clock = new FixedStepClock(60, 5);
    assertEquals(5, clock.advance(2f, 1));
    assertEquals(0f, clock.alpha());
    assertEquals(15, clock.advance(2f, 3));
    assertEquals(0, clock.advance(0f, 1));
  }

  @Test
  void rejectsNonsense() {
    assertThrows(IllegalArgumentException.class, () -> new FixedStepClock(0, 5));
    assertThrows(IllegalArgumentException.class, () -> new FixedStepClock(60, 5).advance(1, 0));
  }
}
