package io.github.yeray06.wavedefense.game.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FrameTimerTest {
  @Test
  void measuresRealTimeBetweenCallsAndClampsStalls() {
    long[] now = {5_000_000_000L};
    FrameTimer timer = new FrameTimer(() -> now[0], 1);
    assertEquals(0f, timer.next());
    now[0] += 16_666_667L;
    assertEquals(1f / 60, timer.next(), 1e-6);
    now[0] += 5_000_000_000L;
    assertEquals(1f, timer.next(), "a long stall is clamped");
    assertEquals(0f, timer.next(), "no time passed");
  }
}
