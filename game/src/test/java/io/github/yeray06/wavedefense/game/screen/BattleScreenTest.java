package io.github.yeray06.wavedefense.game.screen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.yeray06.wavedefense.game.GameTestData;
import io.github.yeray06.wavedefense.game.Headless;
import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.game.playtest.ReloadReactionLog;
import io.github.yeray06.wavedefense.game.render.ShapeWorldRenderer;
import io.github.yeray06.wavedefense.game.ui.UiSkin;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import io.github.yeray06.wavedefense.sim.world.Battle;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The battle screen drives the wave loop and turns a click on a turret into a manual reload. */
class BattleScreenTest {
  @Test
  void runsAWaveAndReloadsTheClickedTurret() {
    List<String> log = new ArrayList<>();
    Headless.run(
        () -> {
          GameData data = GameTestData.load();
          Texts texts = Texts.load(Gdx.files.internal("i18n/messages"));
          Skin skin = UiSkin.create(Gdx.files.internal("fonts/JetBrainsMono-Regular.ttf"));
          ShapeWorldRenderer world = new ShapeWorldRenderer(data);
          WaveLoop loop = new WaveLoop(data, Progress.newGame(data));
          ReloadReactionLog reactions = new ReloadReactionLog(1, 2, log::add);
          BattleScreen screen = new BattleScreen(data, texts, skin, loop, world, reactions);
          screen.resize(1280, 720);

          screen.render(1f / 60);
          assertFalse(loop.state() == WaveLoop.State.RUNNING, "wave 1 waits for deploy");
          loop.deploy();
          Battle battle = loop.battle();
          for (int frame = 0; frame < 2_000 && battle.turret(0).ammo() > 0; frame++) {
            screen.render(1f / 60);
          }
          for (int frame = 0; frame < 30; frame++) {
            screen.render(1f / 60);
          }
          Vector2 point = world.project(battle.turret(0).x(), battle.turret(0).y(), new Vector2());
          assertTrue(screen.clickWorld((int) point.x, (int) point.y));
          assertTrue(battle.turret(0).reloading());
          assertTrue(log.get(0).startsWith("manual reload, wave 1, turret 1"), log.toString());
          assertFalse(screen.clickWorld(5, 5), "a click on empty ground does nothing");

          screen.dispose();
          world.dispose();
          skin.dispose();
        });
  }
}
