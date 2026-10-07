package io.github.yeray06.wavedefense.game.screen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
          BattleScreen screen =
              new BattleScreen(data, texts, skin, loop, world, reactions, new FakeControls(), 1);
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

  @Test
  void panelsToggleAndTheReportOpensItselfWhenAWaveEnds() {
    Headless.run(
        () -> {
          GameData data = GameTestData.load();
          Texts texts = Texts.load(Gdx.files.internal("i18n/messages"));
          Skin skin = UiSkin.create(Gdx.files.internal("fonts/JetBrainsMono-Regular.ttf"));
          ShapeWorldRenderer world = new ShapeWorldRenderer(data);
          WaveLoop loop = new WaveLoop(data, Progress.newGame(data));
          FakeControls controls = new FakeControls();
          BattleScreen screen =
              new BattleScreen(
                  data,
                  texts,
                  skin,
                  loop,
                  world,
                  new ReloadReactionLog(1, 2, line -> {}),
                  controls,
                  1);
          screen.resize(1280, 720);
          screen.toggle(screen.editorPanel());
          assertTrue(screen.openPanel() == screen.editorPanel());
          screen.toggle(screen.editorPanel());
          assertTrue(screen.openPanel() == null);
          screen.toggle(screen.manualScroll());
          assertTrue(screen.openPanel() == screen.manualScroll(), "the manual opens");
          screen.toggle(screen.manualScroll());
          screen.openSettings();
          assertTrue(screen.openPanel() == screen.settingsScroll(), "the settings open");
          screen.toggle(screen.settingsScroll());
          screen.showNotice("aviso");
          assertTrue(screen.notice().equals("aviso"));

          loop.deploy();
          for (int frame = 0; frame < 20_000 && loop.wavesFinished() == 0; frame++) {
            Battle battle = loop.battle();
            if (battle.turret(0).ammo() == 0) {
              loop.manualReload(0);
            }
            screen.render(1f / 20);
          }
          assertTrue(loop.wavesFinished() > 0, "wave 1 ended");
          assertTrue(screen.openPanel() != null, "the report opened by itself");
          screen.render(1f / 60);
          screen.dispose();
          world.dispose();
          skin.dispose();
        });
  }

  /**
   * Yeray: in a window that is not 16:9, buttons answered clicks beside them. The world letterboxes
   * the GL viewport; the HUD must draw over the whole window, where its clicks are mapped.
   */
  @Test
  void theHudDrawsOverTheWholeWindowWhateverItsShape() {
    Headless.run(
        () -> {
          GameData data = GameTestData.load();
          Texts texts = Texts.load(Gdx.files.internal("i18n/messages"));
          Skin skin = UiSkin.create(Gdx.files.internal("fonts/JetBrainsMono-Regular.ttf"));
          ShapeWorldRenderer world = new ShapeWorldRenderer(data);
          WaveLoop loop = new WaveLoop(data, Progress.newGame(data));
          BattleScreen screen =
              new BattleScreen(
                  data,
                  texts,
                  skin,
                  loop,
                  world,
                  new ReloadReactionLog(1, 2, line -> {}),
                  new FakeControls(),
                  1);
          com.badlogic.gdx.graphics.GL20 real = Gdx.gl;
          List<int[]> viewports = new ArrayList<>();
          Gdx.gl =
              Gdx.gl20 =
                  (com.badlogic.gdx.graphics.GL20)
                      java.lang.reflect.Proxy.newProxyInstance(
                          getClass().getClassLoader(),
                          new Class<?>[] {com.badlogic.gdx.graphics.GL20.class},
                          (proxy, method, args) -> {
                            if (method.getName().equals("glViewport")) {
                              viewports.add(
                                  new int[] {
                                    (int) args[0], (int) args[1], (int) args[2], (int) args[3]
                                  });
                            }
                            return method.invoke(real, args);
                          });
          try {
            screen.resize(1920, 1009);
            viewports.clear();
            screen.render(1f / 60);
            int[] last = viewports.get(viewports.size() - 1);
            assertArrayEquals(new int[] {0, 0, 1920, 1009}, last, "the HUD's viewport");
          } finally {
            Gdx.gl = Gdx.gl20 = real;
          }
          screen.dispose();
          world.dispose();
          skin.dispose();
        });
  }
}
