package io.github.yeray06.wavedefense.game.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.game.GameTestData;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import org.junit.jupiter.api.Test;

class AutoSaverTest {
  @Test
  void savesChangesAtMostOncePerIntervalAndAtOnceOnClose() {
    GameData data = GameTestData.load();
    WaveLoop loop = new WaveLoop(data, Progress.newGame(data));
    int[] saves = {0};
    AutoSaver saver =
        new AutoSaver(
            l -> {
              saves[0]++;
              return true;
            },
            1);
    saver.track(loop);
    assertTrue(saver.update(5));
    assertEquals(0, saves[0], "nothing changed");

    loop.setScript(0, "recargar();");
    assertTrue(saver.update(0.1f));
    assertEquals(1, saves[0], "after a quiet while, the first change saves at once");
    loop.setScript(0, "recargar(); recargar();");
    saver.update(0.4f);
    assertEquals(1, saves[0], "not again before the interval");
    saver.update(0.7f);
    assertEquals(2, saves[0]);
    loop.setScript(0, "recargar();");
    saver.update(0.1f);
    assertEquals(2, saves[0]);
    saver.saveIfChanged();
    assertEquals(3, saves[0], "closing saves at once");
    saver.saveIfChanged();
    assertEquals(3, saves[0]);
  }

  @Test
  void aFailedSaveIsReportedAndTriedAgain() {
    GameData data = GameTestData.load();
    WaveLoop loop = new WaveLoop(data, Progress.newGame(data));
    boolean[] works = {false};
    int[] tries = {0};
    AutoSaver saver =
        new AutoSaver(
            l -> {
              tries[0]++;
              return works[0];
            },
            1);
    saver.track(loop);
    loop.setAutoAdvance(false);
    assertFalse(saver.update(2));
    works[0] = true;
    assertTrue(saver.update(2));
    assertEquals(2, tries[0]);
    assertTrue(saver.update(2));
    assertEquals(2, tries[0], "saved: nothing left to do");
  }
}
