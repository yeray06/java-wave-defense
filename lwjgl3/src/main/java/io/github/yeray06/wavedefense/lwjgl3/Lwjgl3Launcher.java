package io.github.yeray06.wavedefense.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import io.github.yeray06.wavedefense.game.WaveDefenseGame;
import io.github.yeray06.wavedefense.platform.NoOpPlatformServices;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Desktop entry point. */
public final class Lwjgl3Launcher {
  private static final String MESSAGES = "i18n/messages.properties";
  private static final String TITLE_KEY = "game.title";
  private static final int WINDOW_WIDTH = 1280;
  private static final int WINDOW_HEIGHT = 720;
  private static final int FOREGROUND_FPS = 60;

  private Lwjgl3Launcher() {}

  public static void main(String[] args) {
    if (StartupHelper.relaunchedOnMainThread()) {
      return;
    }
    WaveDefenseGame game = new WaveDefenseGame(new NoOpPlatformServices());
    Lwjgl3ApplicationConfiguration config = configuration();
    config.setWindowListener(new RedrawWhileMoving(game));
    new Lwjgl3Application(game, config);
  }

  private static Lwjgl3ApplicationConfiguration configuration() {
    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle(loadTitle());
    config.setWindowedMode(WINDOW_WIDTH, WINDOW_HEIGHT);
    config.useVsync(true);
    config.setForegroundFPS(FOREGROUND_FPS);
    return config;
  }

  /** Reads the window title from the Spanish bundle; the window exists before libGDX starts. */
  private static String loadTitle() {
    try (InputStream in = Lwjgl3Launcher.class.getClassLoader().getResourceAsStream(MESSAGES)) {
      if (in == null) {
        throw new IllegalStateException("Missing resource " + MESSAGES);
      }
      Properties messages = new Properties();
      try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
        messages.load(reader);
      }
      String title = messages.getProperty(TITLE_KEY);
      if (title == null) {
        throw new IllegalStateException("Missing key " + TITLE_KEY + " in " + MESSAGES);
      }
      return title;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
