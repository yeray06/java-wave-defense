package io.github.yeray06.wavedefense.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.game.log.GameLog;
import io.github.yeray06.wavedefense.game.loop.FrameTimer;
import io.github.yeray06.wavedefense.game.playtest.ReloadReactionLog;
import io.github.yeray06.wavedefense.game.render.ShapeWorldRenderer;
import io.github.yeray06.wavedefense.game.render.WorldRenderer;
import io.github.yeray06.wavedefense.game.save.AutoSaver;
import io.github.yeray06.wavedefense.game.save.GameStore;
import io.github.yeray06.wavedefense.game.screen.BattleScreen;
import io.github.yeray06.wavedefense.game.screen.GameControls;
import io.github.yeray06.wavedefense.game.settings.Settings;
import io.github.yeray06.wavedefense.game.ui.UiSkin;
import io.github.yeray06.wavedefense.platform.PlatformServices;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import io.github.yeray06.wavedefense.sim.save.SaveCodec;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Composition root of the game: loads data, texts, settings and the saved game, builds the world
 * renderer and the battle screen, saves by itself (D-55) and keeps going when a frame fails
 * (ARCHITECTURE §7).
 */
public final class WaveDefenseGame extends Game implements GameControls {
  private static final String LOG_TAG = "wave-defense";

  /** The window's size when not in full screen. */
  public static final int WINDOW_WIDTH = 1280;

  public static final int WINDOW_HEIGHT = 720;

  /** Decision C: the wave whose manual reloads are timed. */
  private static final int MEASURED_WAVE = 1;

  /** Longest frame passed to the screen; the fixed-step clock caps the ticks anyway. */
  private static final float MAX_FRAME_SECONDS = 1;

  /** At most one autosave per second while the player types. */
  private static final float SAVE_INTERVAL_SECONDS = 1;

  private final FrameTimer frames = new FrameTimer(System::nanoTime, MAX_FRAME_SECONDS);
  private final PlatformServices platform;
  private final Path userFolder;
  private final Clock clock;
  private final Set<String> reportedFailures = new HashSet<>();

  private Logger log;
  private GameData data;
  private Texts texts;
  private GameStore store;
  private AutoSaver autoSaver;
  private Settings settings;
  private WaveLoop loop;
  private ReloadReactionLog reactions;
  private Skin skin;
  private WorldRenderer world;
  private BattleScreen battle;
  private float skinScale;
  private boolean rebuildPending;
  private boolean settingsOpenAfterRebuild;

  /**
   * @param userFolder where the save, the settings and the logs go
   * @param clock only names copies of broken files and stamps the log
   */
  public WaveDefenseGame(PlatformServices platform, Path userFolder, Clock clock) {
    this.platform = platform;
    this.userFolder = userFolder;
    this.clock = clock;
  }

  public PlatformServices platform() {
    return platform;
  }

  @Override
  public void create() {
    log = GameLog.install(userFolder.resolve("logs"));
    Gdx.app.setApplicationLogger(GameLog.forLibgdx(log));
    data = GameData.load(file -> Gdx.files.internal(file).readString("UTF-8"));
    texts = Texts.load(Gdx.files.internal("i18n/messages"));
    store = new GameStore(userFolder, clock, data, SaveCodec.standard(), log);
    settings = store.loadSettings();
    GameStore.Start start = store.loadGame();
    loop = start.loop();
    autoSaver = new AutoSaver(store::save, SAVE_INTERVAL_SECONDS);
    autoSaver.track(loop);
    if (!start.fromSave()) {
      // A first game, or one replacing a broken save: on disk from the start.
      store.save(loop);
    }
    reactions =
        new ReloadReactionLog(
            MEASURED_WAVE, data.map().turretSlots().size(), line -> Gdx.app.log(LOG_TAG, line));
    world = new ShapeWorldRenderer(data);
    applyDisplay(false);
    buildScreen();
    switch (start.notice()) {
      case RECOVERED -> battle.showNotice(texts.get("save.recovered"));
      case LOST ->
          battle.showNotice(
              texts.format(
                  "save.lost",
                  start.setAside().map(Path::toString).orElse(store.folder().toString())));
      case NONE -> {}
    }
  }

  /**
   * Draws a frame with the time measured here, not the backend's delta: the desktop launcher also
   * calls this while the window is moved or resized, when the backend's delta is stale (D-48).
   */
  @Override
  public void render() {
    float delta = frames.next();
    if (rebuildPending) {
      rebuildPending = false;
      buildScreen();
    }
    try {
      if (screen != null) {
        screen.render(delta);
      }
    } catch (RuntimeException e) {
      // ARCHITECTURE §7: a broken frame is logged and the game goes on.
      if (reportedFailures.add(String.valueOf(e))) {
        log.log(Level.SEVERE, "A frame failed", e);
        battle.showNotice(texts.get("error.frame"));
      }
    }
    if (!autoSaver.update(delta)) {
      battle.showNotice(texts.get("save.failed"));
    }
  }

  /** A bigger or smaller window may allow a different interface size (D-56). */
  @Override
  public void resize(int width, int height) {
    super.resize(width, height);
    if (settings != null && effectiveScale(width, height) != skinScale) {
      rebuildPending = true;
    }
  }

  @Override
  public void pause() {
    super.pause();
    if (autoSaver != null) {
      autoSaver.saveIfChanged();
    }
  }

  @Override
  public void dispose() {
    if (autoSaver != null) {
      autoSaver.saveIfChanged();
    }
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

  // ------------------------------------------------------------------ GameControls

  @Override
  public Settings settings() {
    return settings;
  }

  @Override
  public void apply(Settings next) {
    Settings previous = settings;
    settings = next;
    if (!store.saveSettings(next)) {
      battle.showNotice(texts.get("settings.failed"));
    }
    if (next.fullscreen() != previous.fullscreen()) {
      applyDisplay(true);
    }
    if (next.uiScalePercent() != previous.uiScalePercent()
        || next.codeFontSize() != previous.codeFontSize()) {
      // Not now: this runs inside the old screen's click handling.
      rebuildPending = true;
      settingsOpenAfterRebuild = true;
    }
  }

  @Override
  public void newGame() {
    loop = store.newGame();
    if (!store.save(loop)) {
      battle.showNotice(texts.get("save.failed"));
    }
    autoSaver.track(loop);
    rebuildPending = true;
    settingsOpenAfterRebuild = false;
  }

  @Override
  public Path saveFolder() {
    return store.folder();
  }

  /** The game being played, for tests. */
  WaveLoop loop() {
    return loop;
  }

  // ------------------------------------------------------------------ helpers

  /** A new skin at the current sizes and a battle screen on the current game. */
  private void buildScreen() {
    Skin previousSkin = skin;
    BattleScreen previousScreen = battle;
    skin =
        UiSkin.create(
            Gdx.files.internal("fonts/JetBrainsMono-Regular.ttf"),
            settings.uiScale(),
            settings.codeFontSize());
    battle = new BattleScreen(data, texts, skin, loop, world, reactions, this, settings.uiScale());
    setScreen(battle);
    battle.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    if (settingsOpenAfterRebuild) {
      settingsOpenAfterRebuild = false;
      battle.openSettings();
    }
    if (previousScreen != null) {
      previousScreen.dispose();
    }
    if (previousSkin != null) {
      previousSkin.dispose();
    }
  }

  /**
   * The interface size the player chose, as far as the window allows: the layout needs {@link
   * #WINDOW_WIDTH} × {@link #WINDOW_HEIGHT} units, so 150 % needs a 1920 × 1080 window (D-56). Only
   * sizes on offer, so dragging the window edge does not rebuild the fonts on every pixel.
   */
  private float effectiveScale(int width, int height) {
    float fits = Math.min((float) width / WINDOW_WIDTH, (float) height / WINDOW_HEIGHT);
    int best = Settings.UI_SCALES.get(0);
    for (int percent : Settings.UI_SCALES) {
      if (percent <= settings.uiScalePercent() && percent / 100f <= fits) {
        best = percent;
      }
    }
    return best / 100f;
  }

  /**
   * @param fromWindow whether to also leave full screen; at start the window already has its size
   */
  private void applyDisplay(boolean fromWindow) {
    if (settings.fullscreen()) {
      if (!Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode())) {
        log.warning("Full screen refused by the system");
      }
    } else if (fromWindow) {
      Gdx.graphics.setWindowedMode(WINDOW_WIDTH, WINDOW_HEIGHT);
    }
  }
}
