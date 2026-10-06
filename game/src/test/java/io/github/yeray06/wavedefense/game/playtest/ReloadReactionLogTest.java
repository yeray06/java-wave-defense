package io.github.yeray06.wavedefense.game.playtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.game.GameTestData;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import io.github.yeray06.wavedefense.sim.world.Battle;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Decision C: timing the player's manual reloads in wave 1. */
class ReloadReactionLogTest {
  @Test
  void logsTheSecondsFromEmptyToTheClickAndASummaryWhenTheWaveEnds() {
    List<String> lines = new ArrayList<>();
    ReloadReactionLog log = new ReloadReactionLog(1, 2, lines::add);
    WaveLoop loop = new WaveLoop(GameTestData.load(), Progress.newGame(GameTestData.load()));
    loop.deploy();
    Battle battle = loop.battle();
    double now = 0;
    while (battle.turret(0).ammo() > 0) {
      loop.tick();
      now += 1.0 / 60;
      log.afterTick(battle, now);
    }
    double emptyAt = now;
    for (int i = 0; i < 90; i++) {
      loop.tick();
      now += 1.0 / 60;
      log.afterTick(battle, now);
    }
    assertTrue(loop.manualReload(0));
    log.manualReload(battle, 0, now);
    assertEquals(1, lines.size());
    String expected = String.format(java.util.Locale.ROOT, "%.2f s", now - emptyAt);
    assertTrue(lines.get(0).contains(expected), lines.get(0));
    assertTrue(lines.get(0).contains("turret 1"), lines.get(0));

    log.manualReload(battle, 0, now + 1);
    assertEquals(1, lines.size(), "a second click while reloading is not a reaction");
    log.waveFinished(2);
    assertEquals(1, lines.size(), "other waves are not summarised");
    log.waveFinished(1);
    assertTrue(lines.get(1).startsWith("wave 1 manual reloads: 1,"), lines.get(1));
    log.waveFinished(1);
    assertTrue(lines.get(2).contains("no manual reloads"), lines.get(2));
  }

  @Test
  void ignoresOtherWavesAndStaleBattles() {
    List<String> lines = new ArrayList<>();
    ReloadReactionLog log = new ReloadReactionLog(2, 2, lines::add);
    WaveLoop loop = new WaveLoop(GameTestData.load(), Progress.newGame(GameTestData.load()));
    loop.deploy();
    Battle battle = loop.battle();
    while (battle.turret(0).ammo() > 0) {
      loop.tick();
      log.afterTick(battle, 0);
    }
    log.manualReload(battle, 0, 1);
    assertTrue(lines.isEmpty());
  }
}
