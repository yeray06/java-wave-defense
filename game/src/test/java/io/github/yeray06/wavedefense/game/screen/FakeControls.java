package io.github.yeray06.wavedefense.game.screen;

import io.github.yeray06.wavedefense.game.settings.Settings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Game controls that only remember what they were asked. */
public final class FakeControls implements GameControls {
  public Settings settings = Settings.DEFAULTS;
  public final List<Settings> applied = new ArrayList<>();
  public int newGames;

  @Override
  public Settings settings() {
    return settings;
  }

  @Override
  public void apply(Settings next) {
    settings = next;
    applied.add(next);
  }

  @Override
  public void newGame() {
    newGames++;
  }

  @Override
  public Path saveFolder() {
    return Path.of("partida");
  }
}
