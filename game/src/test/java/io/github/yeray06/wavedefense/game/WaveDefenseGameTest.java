package io.github.yeray06.wavedefense.game;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.platform.NoOpPlatformServices;
import org.junit.jupiter.api.Test;

/** Runs the real game (data, texts, font, renderer, battle screen) on the headless backend. */
class WaveDefenseGameTest {
  private static final int FRAMES = 30;

  @Test
  void runsCreateRenderAndDisposeWithoutErrors() {
    int frames = Headless.run(new WaveDefenseGame(new NoOpPlatformServices()), FRAMES);
    assertTrue(frames >= FRAMES, () -> "only " + frames + " frames rendered");
  }
}
