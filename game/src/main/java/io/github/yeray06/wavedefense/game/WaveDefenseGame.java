package io.github.yeray06.wavedefense.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.game.playtest.ReloadReactionLog;
import io.github.yeray06.wavedefense.game.render.ShapeWorldRenderer;
import io.github.yeray06.wavedefense.game.render.WorldRenderer;
import io.github.yeray06.wavedefense.game.screen.BattleScreen;
import io.github.yeray06.wavedefense.game.ui.UiSkin;
import io.github.yeray06.wavedefense.platform.PlatformServices;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;

/**
 * Composition root of the game: loads data, texts and skin, builds the world renderer and the wave
 * loop, and shows the battle screen. Saving (phase 6) is not wired yet: every launch is a new game.
 */
public final class WaveDefenseGame extends Game {
  private static final String LOG_TAG = "wave-defense";

  /** Decision C: the wave whose manual reloads are timed. */
  private static final int MEASURED_WAVE = 1;

  private final PlatformServices platform;
  private Skin skin;
  private WorldRenderer world;

  public WaveDefenseGame(PlatformServices platform) {
    this.platform = platform;
  }

  public PlatformServices platform() {
    return platform;
  }

  @Override
  public void create() {
    GameData data = GameData.load(file -> Gdx.files.internal(file).readString("UTF-8"));
    Texts texts = Texts.load(Gdx.files.internal("i18n/messages"));
    skin = UiSkin.create(Gdx.files.internal("fonts/JetBrainsMono-Regular.ttf"));
    world = new ShapeWorldRenderer(data);
    WaveLoop loop = new WaveLoop(data, Progress.newGame(data));
    ReloadReactionLog reactions =
        new ReloadReactionLog(
            MEASURED_WAVE, data.map().turretSlots().size(), line -> Gdx.app.log(LOG_TAG, line));
    setScreen(new BattleScreen(data, texts, skin, loop, world, reactions));
  }

  @Override
  public void dispose() {
    if (screen != null) {
      screen.dispose();
    }
    if (world != null) {
      world.dispose();
    }
    if (skin != null) {
      skin.dispose();
    }
  }
}
