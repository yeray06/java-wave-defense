package io.github.yeray06.wavedefense.game.screen;

import io.github.yeray06.wavedefense.game.settings.Settings;
import java.nio.file.Path;

/** What the settings panel asks of the game, which owns the save and rebuilds the screens. */
public interface GameControls {
  Settings settings();

  /** Keeps and applies new settings; sizes take effect on the next frame. */
  void apply(Settings settings);

  /**
   * Replaces the game with a new one (the old save stays as the backup).
   *
   * @param expert an expert game: all syntax from the start (D-64)
   */
  void newGame(boolean expert);

  /** An achievement was unlocked (D-63): the store layer hears of it. */
  void achievementUnlocked(String id);

  /** Where the game and the settings are saved, to show the player. */
  Path saveFolder();
}
