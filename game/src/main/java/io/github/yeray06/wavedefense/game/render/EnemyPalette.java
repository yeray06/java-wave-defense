package io.github.yeray06.wavedefense.game.render;

import com.badlogic.gdx.graphics.Color;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;

/** How each enemy kind looks, shared by the world and the UI (briefing, report). */
public final class EnemyPalette {
  private static final Color[] COLOR = new Color[EnemyKind.values().length];
  private static final float[] RADIUS = new float[EnemyKind.values().length];

  static {
    put(EnemyKind.BASIC, new Color(0.85f, 0.40f, 0.40f, 1f), 12);
    put(EnemyKind.REGENERATOR, new Color(0.40f, 0.85f, 0.50f, 1f), 12);
    put(EnemyKind.ARMORED, new Color(0.60f, 0.62f, 0.68f, 1f), 14);
    put(EnemyKind.SWARM, new Color(0.95f, 0.65f, 0.30f, 1f), 7);
    // Small and bright: the eye has to catch it slipping past.
    put(EnemyKind.RUNNER, new Color(0.95f, 0.95f, 0.35f, 1f), 9);
    put(EnemyKind.BOSS, new Color(0.70f, 0.40f, 0.90f, 1f), 26);
  }

  private EnemyPalette() {}

  private static void put(EnemyKind kind, Color color, float radius) {
    COLOR[kind.ordinal()] = color;
    RADIUS[kind.ordinal()] = radius;
  }

  /** Shared instance: do not modify. */
  public static Color color(EnemyKind kind) {
    return COLOR[kind.ordinal()];
  }

  public static float radius(EnemyKind kind) {
    return RADIUS[kind.ordinal()];
  }
}
