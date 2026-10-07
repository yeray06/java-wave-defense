package io.github.yeray06.wavedefense.game.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.game.GameTestData;
import io.github.yeray06.wavedefense.game.settings.Settings;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import io.github.yeray06.wavedefense.sim.save.SaveCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameStoreTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-07T20:00:00Z"), ZoneOffset.UTC);

  @TempDir Path folder;

  private final List<String> logged = new ArrayList<>();

  private GameStore store() {
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    logger.addHandler(
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            logged.add(record.getMessage());
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        });
    GameData data = GameTestData.load();
    return new GameStore(folder, CLOCK, data, SaveCodec.standard(), logger);
  }

  @Test
  void theFirstLaunchIsANewGameWithNothingToSay() {
    GameStore.Start start = store().loadGame();
    assertEquals(GameStore.Notice.NONE, start.notice());
    assertFalse(start.fromSave());
    assertEquals(1, start.loop().battle().wave());
  }

  @Test
  void aSavedGameLoadsWithItsCode() {
    GameStore store = store();
    WaveLoop loop = store.newGame();
    loop.setScript(0, "disparar(); disparar(");
    loop.setAutoAdvance(false);
    assertTrue(store.save(loop));
    GameStore.Start start = store.loadGame();
    assertTrue(start.fromSave());
    assertEquals(GameStore.Notice.NONE, start.notice());
    assertEquals("disparar(); disparar(", start.loop().draft(0));
    assertFalse(start.loop().progress().autoAdvance());
  }

  @Test
  void aBrokenSaveFallsBackToThePreviousOne() throws IOException {
    GameStore store = store();
    WaveLoop loop = store.newGame();
    store.save(loop);
    loop.setScript(0, "recargar();");
    store.save(loop);
    Files.writeString(folder.resolve(GameStore.SAVE), "{\"version\": 1}");
    GameStore.Start start = store.loadGame();
    assertEquals(GameStore.Notice.RECOVERED, start.notice());
    assertEquals("disparar();", start.loop().draft(0), "the previous save");
    assertTrue(start.setAside().isPresent());
    assertFalse(logged.isEmpty(), "the problem is logged");
  }

  @Test
  void whenNothingCanBeReadItIsANewGameAndThePlayerIsTold() throws IOException {
    Files.createDirectories(folder);
    Files.writeString(folder.resolve(GameStore.SAVE), "basura");
    GameStore.Start start = store().loadGame();
    assertEquals(GameStore.Notice.LOST, start.notice());
    assertEquals(
        folder.resolve("save.json.corrupt-20261007-200000"), start.setAside().orElseThrow());
  }

  @Test
  void settingsAreKeptAndBrokenOnesFallBackToDefaults() throws IOException {
    GameStore store = store();
    assertEquals(Settings.DEFAULTS, store.loadSettings());
    Settings mine = Settings.DEFAULTS.withUiScale(125);
    assertTrue(store.saveSettings(mine));
    assertEquals(mine, store.loadSettings());
    Files.writeString(folder.resolve(GameStore.SETTINGS), "{}");
    Files.deleteIfExists(folder.resolve(GameStore.SETTINGS + ".bak"));
    assertEquals(Settings.DEFAULTS, store.loadSettings());
  }

  @Test
  void aSaveThatCannotBeWrittenSaysSoInsteadOfThrowing() throws IOException {
    Files.writeString(folder.resolve("blocked"), "a file, not a folder");
    GameStore store =
        new GameStore(
            folder.resolve("blocked"),
            CLOCK,
            GameTestData.load(),
            SaveCodec.standard(),
            Logger.getAnonymousLogger());
    assertFalse(store.save(store.newGame()));
    assertFalse(store.saveSettings(Settings.DEFAULTS));
  }
}
