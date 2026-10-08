package io.github.yeray06.wavedefense.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationListener;
import io.github.yeray06.wavedefense.game.save.GameStore;
import io.github.yeray06.wavedefense.platform.NoOpPlatformServices;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the real game (data, texts, font, renderer, battle screen) on the headless backend. */
class WaveDefenseGameTest {
  private static final int FRAMES = 30;
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-07T20:00:00Z"), ZoneOffset.UTC);

  @TempDir Path folder;

  private int run() {
    return Headless.run(new WaveDefenseGame(new NoOpPlatformServices(), folder, CLOCK), FRAMES);
  }

  @Test
  void runsCreateRenderAndDisposeWithoutErrorsAndLeavesASaveAndALog() throws IOException {
    int frames = run();
    assertTrue(frames >= FRAMES, () -> "only " + frames + " frames rendered");
    assertTrue(Files.exists(folder.resolve(GameStore.SAVE)), "a new game is saved at once");
    try (Stream<Path> logs = Files.list(folder.resolve("logs"))) {
      assertTrue(logs.anyMatch(p -> p.getFileName().toString().startsWith("game-")));
    }
    // Closed on exit: Windows cannot delete a log the game still holds.
    assertFalse(Files.exists(folder.resolve("logs").resolve("game-0.log.lck")));
  }

  @Test
  void aBrokenSaveIsKeptAsideAndTheGameStillStarts() throws IOException {
    Files.writeString(folder.resolve(GameStore.SAVE), "{ esto no es una partida");
    int frames = run();
    assertTrue(frames >= FRAMES);
    assertTrue(Files.exists(folder.resolve("save.json.corrupt-20261007-200000")));
    String save = Files.readString(folder.resolve(GameStore.SAVE));
    assertTrue(save.contains("\"version\""), "a new game replaced it");
    assertEquals(
        "{ esto no es una partida",
        Files.readString(folder.resolve("save.json.corrupt-20261007-200000")));
  }

  @Test
  void newSizesRebuildTheScreenAndANewGameReplacesTheSave() throws IOException {
    WaveDefenseGame game = new WaveDefenseGame(new NoOpPlatformServices(), folder, CLOCK);
    int[] frame = {0};
    ApplicationListener driver =
        new ApplicationListener() {
          @Override
          public void create() {
            game.create();
          }

          @Override
          public void resize(int width, int height) {
            game.resize(width, height);
          }

          @Override
          public void render() {
            frame[0]++;
            if (frame[0] == 3) {
              game.loop().setScript(0, "recargar();");
              game.apply(game.settings().withUiScale(150).withCodeFontSize(20));
            }
            if (frame[0] == 6) {
              game.newGame(false);
            }
            game.render();
          }

          @Override
          public void pause() {
            game.pause();
          }

          @Override
          public void resume() {
            game.resume();
          }

          @Override
          public void dispose() {
            game.dispose();
          }
        };
    assertTrue(Headless.run(driver, FRAMES) >= FRAMES);
    String settings = Files.readString(folder.resolve(GameStore.SETTINGS));
    assertTrue(settings.contains("\"uiScalePercent\" : 150"), settings);
    assertTrue(settings.contains("\"codeFontSize\" : 20"), settings);
    String save = Files.readString(folder.resolve(GameStore.SAVE));
    assertFalse(save.contains("recargar();"), "the new game replaced the code");
  }

  /**
   * On Windows, showing the window fires a window-moved event, and the launcher draws a frame on
   * those (D-48), before {@code create()} has run.
   */
  @Test
  void aFrameBeforeCreateDoesNothing() {
    WaveDefenseGame game = new WaveDefenseGame(new NoOpPlatformServices(), folder, CLOCK);
    game.render();
    game.resize(1280, 720);
    game.pause();
    game.dispose();
  }
}
