package io.github.yeray06.wavedefense.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.yeray06.wavedefense.platform.PlatformServices;

/**
 * Composition root of the game. Phase 1 only clears the screen; screens, simulation and UI are
 * wired here in later phases.
 */
public final class WaveDefenseGame extends ApplicationAdapter {
  private static final Color BACKGROUND = new Color(0.07f, 0.08f, 0.10f, 1f);

  private final PlatformServices platform;

  public WaveDefenseGame(PlatformServices platform) {
    this.platform = platform;
  }

  public PlatformServices platform() {
    return platform;
  }

  @Override
  public void render() {
    ScreenUtils.clear(BACKGROUND);
  }
}
