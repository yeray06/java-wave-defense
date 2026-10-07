package io.github.yeray06.wavedefense.sim.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.sim.TestData;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.Shop;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** ARCHITECTURE §7: versioned saves, migrations and a strict, checked load. */
class SaveCodecTest {
  private static final String IF_SCRIPT = "if (municion() == 0) recargar();\nelse disparar();";

  private static GameData data() {
    return TestData.load();
  }

  /** A game with wave 1 won, new code in the first turret and auto-advance off. */
  private static WaveLoop played() {
    GameData data = data();
    WaveLoop loop = new WaveLoop(data, Progress.newGame(data));
    loop.deploy();
    int finished = loop.wavesFinished();
    while (loop.wavesFinished() == finished) {
      if (loop.state() == WaveLoop.State.RUNNING && loop.battle().turret(0).ammo() == 0) {
        loop.manualReload(0);
      }
      loop.tick();
    }
    assertTrue(loop.progress().moneyCents() > 0);
    loop.setScript(0, IF_SCRIPT);
    loop.setAutoAdvance(false);
    return loop;
  }

  @Test
  void aSavedGameComesBackAsItWasWaitingOnItsWave() {
    GameData data = data();
    Progress copy = played().progress().copyWithMoney(100_00);
    new Shop(data).buy(copy, Feature.IF_ELSE);
    SaveGame save =
        new SaveGame(
            SaveCodec.VERSION,
            copy.snapshot(),
            List.of(IF_SCRIPT, "disparar();"),
            List.of(IF_SCRIPT, "if (hayEnemigos() {"),
            7);
    SaveCodec codec = SaveCodec.standard();
    String json = codec.write(save);
    assertTrue(json.contains("\"version\" : 1"), json);
    SaveGame read = codec.read(json);
    assertEquals(save, read);

    WaveLoop restored = read.restore(data);
    assertEquals(WaveLoop.State.AWAITING_DEPLOY, restored.state(), "loading never starts a wave");
    assertEquals(copy.snapshot(), restored.progress().snapshot());
    assertEquals(IF_SCRIPT, restored.script(0));
    assertEquals("if (hayEnemigos() {", restored.draft(1), "the broken draft is kept");
    assertEquals("disparar();", restored.script(1));
    assertEquals(8, restored.battlesBuilt(), "the restored wave is battle 7");
  }

  @Test
  void ofCapturesTheLoop() {
    WaveLoop loop = played();
    SaveGame save = SaveGame.of(loop);
    assertEquals(loop.scripts(), save.scripts());
    assertEquals(loop.drafts(), save.drafts());
    assertEquals(loop.progress().snapshot(), save.progress());
    assertEquals(SaveGame.of(save.restore(data())).progress(), save.progress());
  }

  @Test
  void aScriptThatNoLongerCompilesFallsBackToTheInitialOneButKeepsTheDraft() {
    GameData data = data();
    Progress progress = Progress.newGame(data);
    SaveGame save =
        new SaveGame(
            SaveCodec.VERSION,
            progress.snapshot(),
            List.of("while (true) disparar();"),
            List.of("while (true) disparar();"),
            0);
    WaveLoop restored = save.restore(data);
    assertEquals(data.turret().initialScript(), restored.script(0));
    assertEquals("while (true) disparar();", restored.draft(0));
  }

  @Test
  void savesThatBreakTheRulesAreRejected() {
    GameData data = data();
    Progress.Snapshot fine = Progress.newGame(data).snapshot();
    assertRejected(data, withWaves(fine, 3, 2), "current wave");
    assertRejected(data, withWaves(fine, 1, 11), "highest wave");
    assertRejected(
        data,
        new Progress.Snapshot(
            0, Set.of(Feature.WHILE), fine.levels(), 1, 1, true, Set.of(), Set.of(), Set.of()),
        "WHILE without LOGIC_OPS");
    assertRejected(
        data,
        new Progress.Snapshot(
            -1, Set.of(), fine.levels(), 1, 1, true, Set.of(), Set.of(), Set.of()),
        "money");
    assertRejected(
        data,
        new Progress.Snapshot(
            0,
            Set.of(),
            Map.of(HardwareKind.EXTRA_TURRET, 5),
            1,
            1,
            true,
            Set.of(),
            Set.of(),
            Set.of()),
        "slots");
  }

  private static Progress.Snapshot withWaves(Progress.Snapshot s, int current, int highest) {
    return new Progress.Snapshot(
        s.moneyCents(),
        s.features(),
        s.levels(),
        current,
        highest,
        s.autoAdvance(),
        s.beatenWaves(),
        s.perfectWaves(),
        s.bossesKilled());
  }

  private static void assertRejected(GameData data, Progress.Snapshot snapshot, String part) {
    SaveGame save = new SaveGame(SaveCodec.VERSION, snapshot, List.of(), List.of(), 0);
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> save.restore(data));
    assertTrue(e.getMessage().contains(part), e.getMessage());
  }

  @Test
  void brokenNewerOrUnknownFilesAreRejected() {
    SaveCodec codec = SaveCodec.standard();
    String json = codec.write(SaveGame.of(new WaveLoop(data(), Progress.newGame(data()))));
    assertRejected(codec, "{ \"version\": 1, \"progr", "broken");
    assertRejected(codec, "[]", "no version");
    assertRejected(codec, json.replace("\"version\" : 1", "\"version\" : 2"), "newer game");
    assertRejected(codec, json.replace("\"drafts\"", "\"drafts\" : [], \"extra\""), "extra");
    assertRejected(codec, json.replace("\"version\" : 1", "\"version\" : 0"), "no migration");
  }

  private static void assertRejected(SaveCodec codec, String json, String part) {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> codec.read(json));
    assertTrue(e.getMessage().contains(part), e.getMessage());
  }

  @Test
  void olderVersionsAreMigratedStepByStep() {
    SaveCodec current = SaveCodec.standard();
    String json = current.write(SaveGame.of(new WaveLoop(data(), Progress.newGame(data()))));
    // A pretend version 0 that called the scripts "code".
    String old =
        json.replace("\"version\" : 1", "\"version\" : 0").replace("\"scripts\"", "\"code\"");
    SaveCodec.Migration renameCode =
        new SaveCodec.Migration() {
          @Override
          public int from() {
            return 0;
          }

          @Override
          public void apply(com.fasterxml.jackson.databind.node.ObjectNode save) {
            save.set("scripts", save.remove("code"));
          }
        };
    SaveCodec withMigration = new SaveCodec(SaveCodec.VERSION, List.of(renameCode));
    assertEquals(current.read(json), withMigration.read(old));
  }
}
