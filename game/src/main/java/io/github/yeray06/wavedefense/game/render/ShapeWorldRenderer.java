package io.github.yeray06.wavedefense.game.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.MapConfig;
import io.github.yeray06.wavedefense.sim.world.Battle;
import io.github.yeray06.wavedefense.sim.world.BattleEvents;
import io.github.yeray06.wavedefense.sim.world.Bullet;
import io.github.yeray06.wavedefense.sim.world.Enemy;
import io.github.yeray06.wavedefense.sim.world.Turret;
import java.util.List;

/**
 * The MVP's world: flat shapes with {@link ShapeRenderer} on a 1280×720 {@link FitViewport}, one
 * world unit per virtual pixel, centred on the map. Sizes and colours here are presentation only;
 * every rule and number of the game comes from the simulation.
 */
public final class ShapeWorldRenderer implements WorldRenderer {
  private static final float VIEW_WIDTH = 1280;
  private static final float VIEW_HEIGHT = 720;
  private static final float PATH_WIDTH = 34;
  private static final float TURRET_HALF = 20;
  private static final float BAR_WIDTH = 44;
  private static final float BAR_HEIGHT = 5;
  private static final float CORE_RADIUS = 30;
  private static final float BULLET_RADIUS = 3.5f;
  private static final float PICK_SLACK = 8;
  private static final int MAX_EFFECTS = 512;

  private static final Color PATH = new Color(0.16f, 0.18f, 0.22f, 1f);
  private static final Color PATH_EDGE = new Color(0.24f, 0.27f, 0.33f, 1f);
  private static final Color SPAWN = new Color(0.45f, 0.30f, 0.30f, 1f);
  private static final Color CORE = new Color(0.35f, 0.75f, 0.95f, 1f);
  private static final Color CORE_LOW = new Color(0.95f, 0.35f, 0.35f, 1f);
  private static final Color SLOT = new Color(0.30f, 0.33f, 0.38f, 1f);
  private static final Color TURRET = new Color(0.55f, 0.62f, 0.72f, 1f);
  private static final Color TURRET_RELOADING = new Color(0.90f, 0.78f, 0.35f, 1f);
  private static final Color TURRET_HUNG = new Color(0.85f, 0.30f, 0.30f, 1f);
  private static final Color TURRET_IDLE = new Color(0.32f, 0.34f, 0.38f, 1f);
  private static final Color RANGE = new Color(0.55f, 0.62f, 0.72f, 0.18f);
  private static final Color AMMO = new Color(0.90f, 0.85f, 0.45f, 1f);
  private static final Color HEAT = new Color(0.95f, 0.45f, 0.25f, 1f);
  private static final Color BAR_BACK = new Color(0.10f, 0.11f, 0.13f, 1f);
  private static final Color HEALTH = new Color(0.45f, 0.90f, 0.45f, 1f);
  private static final Color ARMOR_RING = new Color(0.80f, 0.82f, 0.86f, 1f);
  private static final Color BULLET_NORMAL = new Color(1f, 0.92f, 0.55f, 1f);
  private static final Color BULLET_PIERCING = new Color(0.45f, 0.95f, 1f, 1f);
  private static final Color[] ENEMY = new Color[EnemyKind.values().length];
  private static final float[] ENEMY_RADIUS = new float[EnemyKind.values().length];

  static {
    put(EnemyKind.BASIC, new Color(0.85f, 0.40f, 0.40f, 1f), 12);
    put(EnemyKind.REGENERATOR, new Color(0.40f, 0.85f, 0.50f, 1f), 12);
    put(EnemyKind.ARMORED, new Color(0.60f, 0.62f, 0.68f, 1f), 14);
    put(EnemyKind.SWARM, new Color(0.95f, 0.65f, 0.30f, 1f), 7);
    put(EnemyKind.BOSS, new Color(0.70f, 0.40f, 0.90f, 1f), 26);
  }

  private static void put(EnemyKind kind, Color color, float radius) {
    ENEMY[kind.ordinal()] = color;
    ENEMY_RADIUS[kind.ordinal()] = radius;
  }

  /** Short-lived visual effects started by the battle's events. */
  private enum Effect {
    MUZZLE(0.10f, 6, 22, BULLET_NORMAL),
    AIR(0.15f, 6, 18, new Color(0.6f, 0.6f, 0.6f, 1f)),
    SPARK(0.12f, 2, 10, Color.WHITE),
    BLOCKED(0.15f, 2, 12, ARMOR_RING),
    KILL(0.35f, 6, 34, Color.WHITE),
    LEAK(0.6f, CORE_RADIUS, CORE_RADIUS + 40, CORE_LOW),
    FAULT(0.5f, TURRET_HALF, TURRET_HALF + 30, TURRET_HUNG),
    RELOAD(0.3f, TURRET_HALF, TURRET_HALF + 16, TURRET_RELOADING);

    final float life;
    final float fromRadius;
    final float toRadius;
    final Color color;

    Effect(float life, float fromRadius, float toRadius, Color color) {
      this.life = life;
      this.fromRadius = fromRadius;
      this.toRadius = toRadius;
      this.color = color;
    }
  }

  private static final Effect[] EFFECTS = Effect.values();

  private final OrthographicCamera camera = new OrthographicCamera();
  private final Viewport viewport = new FitViewport(VIEW_WIDTH, VIEW_HEIGHT, camera);
  private final ShapeRenderer shapes = new ShapeRenderer();
  private final List<MapConfig.Point> path;
  private final List<MapConfig.Point> slots;
  private final double range;
  private final int maxHeat;
  private final int maxLives;

  private final int[] effect = new int[MAX_EFFECTS];
  private final float[] effectX = new float[MAX_EFFECTS];
  private final float[] effectY = new float[MAX_EFFECTS];
  private final float[] effectAge = new float[MAX_EFFECTS];
  private int effects;
  private int windowHeight;

  public ShapeWorldRenderer(GameData data) {
    path = data.map().path();
    slots = data.map().turretSlots();
    range = data.turret().rangeUnits();
    maxHeat = data.turret().maxHeat();
    maxLives = data.balance().coreLivesPerWave();
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    for (List<MapConfig.Point> points : List.of(path, slots)) {
      for (MapConfig.Point p : points) {
        minX = Math.min(minX, (float) p.x());
        minY = Math.min(minY, (float) p.y());
        maxX = Math.max(maxX, (float) p.x());
        maxY = Math.max(maxY, (float) p.y());
      }
    }
    camera.position.set((minX + maxX) / 2, (minY + maxY) / 2, 0);
    camera.update();
  }

  @Override
  public void resize(int width, int height) {
    windowHeight = height;
    viewport.update(width, height, false);
  }

  @Override
  public void render(Battle battle, float alpha, float deltaSeconds) {
    startEffects(battle.events());
    viewport.apply();
    shapes.setProjectionMatrix(camera.combined);
    com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_BLEND);
    com.badlogic.gdx.Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

    shapes.begin(ShapeRenderer.ShapeType.Filled);
    drawPath();
    drawCore(battle);
    drawTurrets(battle);
    drawEnemies(battle, alpha);
    drawBullets(battle, alpha);
    shapes.end();

    shapes.begin(ShapeRenderer.ShapeType.Line);
    drawRanges(battle);
    drawArmor(battle, alpha);
    drawEffects(deltaSeconds);
    shapes.end();
    com.badlogic.gdx.Gdx.gl.glDisable(GL20.GL_BLEND);
  }

  private void drawPath() {
    for (int i = 1; i < path.size(); i++) {
      MapConfig.Point a = path.get(i - 1);
      MapConfig.Point b = path.get(i);
      shapes.setColor(PATH_EDGE);
      shapes.rectLine((float) a.x(), (float) a.y(), (float) b.x(), (float) b.y(), PATH_WIDTH + 6);
      shapes.circle((float) b.x(), (float) b.y(), (PATH_WIDTH + 6) / 2, 24);
    }
    for (int i = 1; i < path.size(); i++) {
      MapConfig.Point a = path.get(i - 1);
      MapConfig.Point b = path.get(i);
      shapes.setColor(PATH);
      shapes.rectLine((float) a.x(), (float) a.y(), (float) b.x(), (float) b.y(), PATH_WIDTH);
      shapes.circle((float) b.x(), (float) b.y(), PATH_WIDTH / 2, 24);
    }
    MapConfig.Point start = path.get(0);
    shapes.setColor(SPAWN);
    shapes.rect((float) start.x() - 8, (float) start.y() - PATH_WIDTH / 2, 16, PATH_WIDTH);
  }

  private void drawCore(Battle battle) {
    MapConfig.Point end = path.get(path.size() - 1);
    float health = Math.max(0, battle.coreLives()) / (float) maxLives;
    shapes.setColor(
        CORE_LOW.r + (CORE.r - CORE_LOW.r) * health,
        CORE_LOW.g + (CORE.g - CORE_LOW.g) * health,
        CORE_LOW.b + (CORE.b - CORE_LOW.b) * health,
        1f);
    shapes.circle((float) end.x(), (float) end.y(), CORE_RADIUS, 40);
    shapes.setColor(BAR_BACK);
    shapes.circle((float) end.x(), (float) end.y(), CORE_RADIUS * 0.45f, 24);
  }

  private void drawTurrets(Battle battle) {
    for (int i = battle.turretCount(); i < slots.size(); i++) {
      MapConfig.Point slot = slots.get(i);
      shapes.setColor(SLOT);
      shapes.rect(
          (float) slot.x() - TURRET_HALF / 2,
          (float) slot.y() - TURRET_HALF / 2,
          TURRET_HALF,
          TURRET_HALF);
    }
    for (int i = 0; i < battle.turretCount(); i++) {
      Turret turret = battle.turret(i);
      float x = (float) turret.x();
      float y = (float) turret.y();
      Color color =
          !turret.hasScript()
              ? TURRET_IDLE
              : turret.hung() ? TURRET_HUNG : turret.reloading() ? TURRET_RELOADING : TURRET;
      shapes.setColor(color);
      shapes.rect(x - TURRET_HALF, y - TURRET_HALF, TURRET_HALF * 2, TURRET_HALF * 2);
      shapes.setColor(BAR_BACK);
      shapes.circle(x, y, TURRET_HALF * 0.45f, 20);
      float barX = x - BAR_WIDTH / 2;
      float barY = y - TURRET_HALF - 2 * BAR_HEIGHT - 4;
      bar(barX, barY + BAR_HEIGHT + 2, turret.ammo() / (float) battle.magazine(), AMMO);
      bar(barX, barY, turret.heat() / (float) maxHeat, HEAT);
    }
  }

  private void bar(float x, float y, float fraction, Color color) {
    shapes.setColor(BAR_BACK);
    shapes.rect(x, y, BAR_WIDTH, BAR_HEIGHT);
    shapes.setColor(color);
    shapes.rect(x, y, BAR_WIDTH * Math.max(0, Math.min(1, fraction)), BAR_HEIGHT);
  }

  private void drawEnemies(Battle battle, float alpha) {
    for (int i = 0; i < battle.enemies().capacity(); i++) {
      Enemy enemy = battle.enemies().get(i);
      if (!enemy.active()) {
        continue;
      }
      float x = lerp(enemy.prevX(), enemy.x(), alpha);
      float y = lerp(enemy.prevY(), enemy.y(), alpha);
      int kind = enemy.kind().ordinal();
      float radius = ENEMY_RADIUS[kind];
      shapes.setColor(ENEMY[kind]);
      shapes.circle(x, y, radius, 20);
      if (enemy.health() < enemy.maxHealth()) {
        float width = Math.max(16, radius * 2);
        float fraction = (float) (enemy.health() / enemy.maxHealth());
        shapes.setColor(BAR_BACK);
        shapes.rect(x - width / 2, y + radius + 4, width, 3);
        shapes.setColor(HEALTH);
        shapes.rect(x - width / 2, y + radius + 4, width * Math.max(0, fraction), 3);
      }
    }
  }

  private void drawBullets(Battle battle, float alpha) {
    for (int i = 0; i < battle.bullets().capacity(); i++) {
      Bullet bullet = battle.bullets().get(i);
      if (!bullet.active()) {
        continue;
      }
      shapes.setColor(bullet.ammo() == AmmoType.PIERCING ? BULLET_PIERCING : BULLET_NORMAL);
      shapes.circle(
          lerp(bullet.prevX(), bullet.x(), alpha),
          lerp(bullet.prevY(), bullet.y(), alpha),
          BULLET_RADIUS,
          10);
    }
  }

  private void drawRanges(Battle battle) {
    shapes.setColor(RANGE);
    for (int i = 0; i < battle.turretCount(); i++) {
      Turret turret = battle.turret(i);
      shapes.circle((float) turret.x(), (float) turret.y(), (float) range, 72);
    }
  }

  private void drawArmor(Battle battle, float alpha) {
    shapes.setColor(ARMOR_RING);
    for (int i = 0; i < battle.enemies().capacity(); i++) {
      Enemy enemy = battle.enemies().get(i);
      if (enemy.active() && enemy.armored()) {
        shapes.circle(
            lerp(enemy.prevX(), enemy.x(), alpha),
            lerp(enemy.prevY(), enemy.y(), alpha),
            ENEMY_RADIUS[enemy.kind().ordinal()] + 3,
            20);
      }
    }
  }

  private void startEffects(BattleEvents events) {
    for (int i = 0; i < events.size(); i++) {
      Effect started =
          switch (events.type(i)) {
            case SHOT -> Effect.MUZZLE;
            case AIR_SHOT -> Effect.AIR;
            case HIT -> events.b(i) == 1 ? Effect.BLOCKED : Effect.SPARK;
            case KILL -> Effect.KILL;
            case LEAK -> Effect.LEAK;
            case FAULT -> Effect.FAULT;
            case RELOAD -> Effect.RELOAD;
            case EMPTY -> null;
          };
      if (started == null) {
        continue;
      }
      float x = (float) events.x(i);
      float y = (float) events.y(i);
      if (started == Effect.LEAK) {
        MapConfig.Point end = path.get(path.size() - 1);
        x = (float) end.x();
        y = (float) end.y();
      }
      addEffect(started, x, y);
    }
    events.clear();
  }

  private void addEffect(Effect type, float x, float y) {
    if (effects == MAX_EFFECTS) {
      return;
    }
    effect[effects] = type.ordinal();
    effectX[effects] = x;
    effectY[effects] = y;
    effectAge[effects] = 0;
    effects++;
  }

  private void drawEffects(float deltaSeconds) {
    int kept = 0;
    for (int i = 0; i < effects; i++) {
      Effect type = EFFECTS[effect[i]];
      float age = effectAge[i] + deltaSeconds;
      if (age >= type.life) {
        continue;
      }
      float t = age / type.life;
      shapes.setColor(type.color.r, type.color.g, type.color.b, 1 - t);
      shapes.circle(
          effectX[i], effectY[i], type.fromRadius + (type.toRadius - type.fromRadius) * t, 24);
      effect[kept] = effect[i];
      effectX[kept] = effectX[i];
      effectY[kept] = effectY[i];
      effectAge[kept] = age;
      kept++;
    }
    effects = kept;
  }

  private static float lerp(double from, double to, float alpha) {
    return (float) (from + (to - from) * alpha);
  }

  @Override
  public int turretAt(Battle battle, int screenX, int screenY) {
    float worldX = left() + (screenX - viewport.getScreenX()) * scaleX();
    float worldY = bottom() + (windowHeight - screenY - viewport.getScreenY()) * scaleY();
    float reach = TURRET_HALF + PICK_SLACK;
    for (int i = 0; i < battle.turretCount(); i++) {
      Turret turret = battle.turret(i);
      if (Math.abs(worldX - turret.x()) <= reach && Math.abs(worldY - turret.y()) <= reach) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public Vector2 project(double worldX, double worldY, Vector2 out) {
    float x = viewport.getScreenX() + ((float) worldX - left()) / scaleX();
    float y = viewport.getScreenY() + ((float) worldY - bottom()) / scaleY();
    return out.set(x, windowHeight - y);
  }

  // Screen ↔ world by hand: the 2D camera never zooms or rotates, and Camera.unproject flips y
  // with the global window height, which this renderer already knows from resize().

  private float left() {
    return camera.position.x - VIEW_WIDTH / 2;
  }

  private float bottom() {
    return camera.position.y - VIEW_HEIGHT / 2;
  }

  private float scaleX() {
    return VIEW_WIDTH / viewport.getScreenWidth();
  }

  private float scaleY() {
    return VIEW_HEIGHT / viewport.getScreenHeight();
  }

  @Override
  public void dispose() {
    shapes.dispose();
  }
}
