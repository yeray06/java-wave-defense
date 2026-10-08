package io.github.yeray06.wavedefense.game.save;

import io.github.yeray06.wavedefense.game.settings.Settings;
import io.github.yeray06.wavedefense.platform.AtomicFileStore;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.progress.Career;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import io.github.yeray06.wavedefense.sim.save.SaveCodec;
import io.github.yeray06.wavedefense.sim.save.SaveGame;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The single saved game and the settings, in the user folder (ARCHITECTURE §7). Never throws for a
 * bad or unwritable file: it logs, falls back to the backup or a new game, and says so.
 */
public final class GameStore {
  public static final String SAVE = "save.json";
  public static final String SETTINGS = "settings.json";

  /** What the player should be told after loading. */
  public enum Notice {
    /** Nothing to say: the save loaded, or there was none yet. */
    NONE,
    /** The save could not be read; its previous version was. */
    RECOVERED,
    /** Neither could be read: a new game, the broken files kept aside. */
    LOST
  }

  /**
   * @param setAside where the unreadable save was kept, if anywhere
   * @param fromSave whether the game came from {@code save.json} itself
   */
  public record Start(WaveLoop loop, Notice notice, Optional<Path> setAside, boolean fromSave) {}

  private final Path folder;
  private final GameData data;
  private final SaveCodec codec;
  private final AtomicFileStore saves;
  private final AtomicFileStore settings;
  private final Logger log;

  public GameStore(Path folder, Clock clock, GameData data, SaveCodec codec, Logger log) {
    this.folder = folder;
    this.data = data;
    this.codec = codec;
    this.saves = new AtomicFileStore(folder.resolve(SAVE), clock);
    this.settings = new AtomicFileStore(folder.resolve(SETTINGS), clock);
    this.log = log;
  }

  public Path folder() {
    return folder;
  }

  /** The saved game, its backup, or a new game. */
  public Start loadGame() {
    var loaded = saves.read(json -> codec.read(json).restore(data));
    loaded.problems().forEach(problem -> log.warning("Save not readable: " + problem));
    Optional<Path> aside = loaded.setAside().stream().findFirst();
    return switch (loaded.source()) {
      case MAIN -> new Start(loaded.value().orElseThrow(), Notice.NONE, aside, true);
      case BACKUP -> new Start(loaded.value().orElseThrow(), Notice.RECOVERED, aside, false);
      case NONE ->
          new Start(
              newGame(), loaded.setAside().isEmpty() ? Notice.NONE : Notice.LOST, aside, false);
    };
  }

  public WaveLoop newGame() {
    return newGame(false);
  }

  /** A new game; an expert one starts with all syntax (D-64). */
  public WaveLoop newGame(boolean expert) {
    return new WaveLoop(
        data,
        expert ? Progress.newExpertGame(data) : Progress.newGame(data),
        Career.newGame(data, expert));
  }

  /**
   * @return false if it could not be written (logged); the game goes on and tries again later
   */
  public boolean save(WaveLoop loop) {
    try {
      saves.write(codec.write(SaveGame.of(loop)));
      return true;
    } catch (IOException | RuntimeException e) {
      log.log(Level.WARNING, "Cannot save the game", e);
      return false;
    }
  }

  /** The saved settings, or the defaults. */
  public Settings loadSettings() {
    var loaded = settings.read(Settings::fromJson);
    loaded.problems().forEach(problem -> log.warning("Settings not readable: " + problem));
    return loaded.value().orElse(Settings.DEFAULTS);
  }

  public boolean saveSettings(Settings values) {
    try {
      settings.write(values.toJson());
      return true;
    } catch (IOException | RuntimeException e) {
      log.log(Level.WARNING, "Cannot save the settings", e);
      return false;
    }
  }
}
