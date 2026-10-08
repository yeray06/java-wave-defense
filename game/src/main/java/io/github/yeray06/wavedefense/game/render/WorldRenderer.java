package io.github.yeray06.wavedefense.game.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import io.github.yeray06.wavedefense.sim.data.CareerConfig;
import io.github.yeray06.wavedefense.sim.world.Battle;

/**
 * Everything that draws the world (D-40): path, core, turrets, enemies, bullets and effects. It
 * only reads the simulation and owns the camera, so a 3D implementation can replace the 2D one
 * without touching screens, UI or core-sim. The UI (scene2d) does not go through here; it asks this
 * interface where world points land on screen.
 *
 * <p>Screen coordinates follow libGDX input: origin top left, y down.
 */
public interface WorldRenderer extends Disposable {
  /** The window changed size. */
  void resize(int width, int height);

  /**
   * Draws the battle and plays the effects of its pending events, then clears them.
   *
   * @param alpha how far between the previous tick and the current one, in [0, 1]
   * @param deltaSeconds real time since the last frame, for effects
   */
  void render(Battle battle, float alpha, float deltaSeconds);

  /** The built turret under a screen point, or -1. */
  int turretAt(Battle battle, int screenX, int screenY);

  /** The pool index of the live enemy under a screen point, or -1: for its tooltip. */
  int enemyAt(Battle battle, int screenX, int screenY);

  /**
   * The hat a turret slot wears (D-63), drawn on top of it; {@code null} takes it off. Cosmetic
   * state of the player's, not of the simulation.
   */
  void setHat(int slot, CareerConfig.HatShape shape, Color color);

  /** Where a world point lands on screen; writes into {@code out} and returns it. */
  Vector2 project(double worldX, double worldY, Vector2 out);
}
