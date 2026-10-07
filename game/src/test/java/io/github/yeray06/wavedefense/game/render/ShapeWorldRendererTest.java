package io.github.yeray06.wavedefense.game.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Vector2;
import io.github.yeray06.wavedefense.game.GameTestData;
import io.github.yeray06.wavedefense.game.Headless;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.PurchaseCheck;
import io.github.yeray06.wavedefense.sim.progress.Shop;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import io.github.yeray06.wavedefense.sim.world.Battle;
import org.junit.jupiter.api.Test;

/** D-40: the 2D renderer's picking and projection agree, and drawing consumes the events. */
class ShapeWorldRendererTest {
  @Test
  void projectedTurretsArePickedBackAtAnyWindowSize() {
    Headless.run(
        () -> {
          ShapeWorldRenderer renderer = new ShapeWorldRenderer(GameTestData.load());
          Battle battle = twoTurrets().battle();
          for (int[] size : new int[][] {{1280, 720}, {1920, 1080}, {1600, 720}, {800, 900}}) {
            renderer.resize(size[0], size[1]);
            Vector2 point = new Vector2();
            for (int i = 0; i < battle.turretCount(); i++) {
              renderer.project(battle.turret(i).x(), battle.turret(i).y(), point);
              assertEquals(i, renderer.turretAt(battle, (int) point.x, (int) point.y));
            }
            renderer.project(battle.turret(0).x(), battle.turret(0).y() + 200, point);
            assertEquals(-1, renderer.turretAt(battle, (int) point.x, (int) point.y));
          }
          renderer.dispose();
        });
  }

  @Test
  void renderingAWaveConsumesItsEvents() {
    Headless.run(
        () -> {
          ShapeWorldRenderer renderer = new ShapeWorldRenderer(GameTestData.load());
          renderer.resize(1280, 720);
          WaveLoop loop = twoTurrets();
          loop.deploy();
          for (int frame = 0; frame < 400; frame++) {
            for (int t = 0; t < 3; t++) {
              loop.tick();
            }
            renderer.render(loop.battle(), 0.5f, 1f / 60);
            assertEquals(0, loop.battle().events().size());
          }
          renderer.dispose();
        });
  }

  /** A new game with the second turret bought (shop caps lifted), so both slots are drawn. */
  private static WaveLoop twoTurrets() {
    Progress progress = Progress.newGame(GameTestData.load()).copyWithMoney(1_000_000);
    Shop shop = new Shop(GameTestData.load().withoutHardwareCaps());
    assertEquals(PurchaseCheck.OK, shop.buy(progress, HardwareKind.EXTRA_TURRET));
    return new WaveLoop(GameTestData.load(), progress);
  }
}
