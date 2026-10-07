package io.github.yeray06.wavedefense.game.panel;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import java.util.function.IntConsumer;

/**
 * The wave map (D-49), in the style of a level map: the waves as points along a winding road from
 * the bottom up. Reached waves can be chosen; the others carry a padlock; a red flag marks the
 * current one; the boss wave is a bigger red point. One map per act: the MVP has act 1.
 */
public final class MapPanel extends Table {
  private static final float MAP_WIDTH = 460;
  private static final float MAP_HEIGHT = 470;
  private static final float NODE = 30;
  private static final float BOSS_NODE = 42;
  private static final float DOT = 6;
  private static final int DOTS_BETWEEN = 3;

  private static final Color REACHED = new Color(0.92f, 0.92f, 0.88f, 1f);
  private static final Color LOCKED = new Color(0.30f, 0.32f, 0.36f, 1f);
  private static final Color BOSS = new Color(0.85f, 0.20f, 0.20f, 1f);
  private static final Color FLAG = new Color(0.90f, 0.15f, 0.15f, 1f);
  private static final Color ROAD = new Color(0.55f, 0.45f, 0.30f, 1f);
  private static final Color CURRENT_RING = new Color(0.40f, 0.90f, 0.40f, 1f);

  private final GameData data;
  private final Progress progress;
  private final Texts texts;
  private final Skin skin;
  private final IntConsumer onChoose;
  private final Group map = new Group();
  private final Label hint;
  private int shownCurrent = -1;
  private int shownHighest = -1;

  /**
   * @param onChoose called with a reached wave the player clicked
   */
  public MapPanel(GameData data, Progress progress, Texts texts, Skin skin, IntConsumer onChoose) {
    this.data = data;
    this.progress = progress;
    this.texts = texts;
    this.skin = skin;
    this.onChoose = onChoose;
    map.setSize(MAP_WIDTH, MAP_HEIGHT);
    hint = new Label("", skin, "small");
    top().left();
    add(new Label(texts.format("map.act", 1), skin, "title")).left().padBottom(6).row();
    add(map).size(MAP_WIDTH, MAP_HEIGHT).row();
    add(hint).left().padTop(6);
  }

  @Override
  public void act(float delta) {
    super.act(delta);
    if (progress.currentWave() != shownCurrent || progress.highestWaveReached() != shownHighest) {
      rebuild();
    }
  }

  /** Where wave {@code wave} (1-based) sits: a zigzag from the bottom to the top. */
  float[] position(int wave) {
    int last = data.balance().lastWave();
    float t = (wave - 1) / (float) Math.max(1, last - 1);
    float y = 30 + t * (MAP_HEIGHT - 80);
    float x =
        MAP_WIDTH / 2 + (wave % 2 == 0 ? 1 : -1) * MAP_WIDTH * 0.22f * (wave % 4 < 2 ? 1 : 0.6f);
    return new float[] {x, y};
  }

  private void rebuild() {
    shownCurrent = progress.currentWave();
    shownHighest = progress.highestWaveReached();
    map.clearChildren();
    int last = data.balance().lastWave();
    for (int wave = 1; wave < last; wave++) {
      float[] from = position(wave);
      float[] to = position(wave + 1);
      for (int d = 1; d <= DOTS_BETWEEN; d++) {
        float t = d / (float) (DOTS_BETWEEN + 1);
        Image dot = new Image(skin.getDrawable("circle"));
        dot.setColor(wave + 1 <= shownHighest ? ROAD : LOCKED);
        dot.setBounds(
            from[0] + (to[0] - from[0]) * t - DOT / 2,
            from[1] + (to[1] - from[1]) * t - DOT / 2,
            DOT,
            DOT);
        dot.setTouchable(Touchable.disabled);
        map.addActor(dot);
      }
    }
    for (int wave = 1; wave <= last; wave++) {
      map.addActor(node(wave));
    }
    hint.setText(texts.format("map.current", shownCurrent));
  }

  private Group node(int wave) {
    boolean boss = data.waves().wave(wave).hasBoss();
    boolean reached = wave <= shownHighest;
    float size = boss ? BOSS_NODE : NODE;
    float[] at = position(wave);
    Group node = new Group();
    node.setBounds(at[0] - size / 2, at[1] - size / 2, size, size);
    if (wave == shownCurrent) {
      Image ring = new Image(skin.getDrawable("ring"));
      ring.setColor(CURRENT_RING);
      ring.setBounds(-4, -4, size + 8, size + 8);
      node.addActor(ring);
    }
    Image point = new Image(skin.getDrawable("circle"));
    point.setColor(!reached ? LOCKED : boss ? BOSS : REACHED);
    point.setBounds(0, 0, size, size);
    node.addActor(point);
    if (!reached) {
      Image lock = new Image(skin.getDrawable("lock"));
      lock.setColor(Color.LIGHT_GRAY);
      lock.setBounds(size * 0.2f, size * 0.2f, size * 0.6f, size * 0.6f);
      node.addActor(lock);
    }
    if (wave == shownCurrent) {
      Image flag = new Image(skin.getDrawable("flag"));
      flag.setColor(FLAG);
      flag.setBounds(size * 0.55f, size * 0.55f, 26, 26);
      node.addActor(flag);
    }
    Label label = new Label(texts.format("map.node", wave), skin, "small");
    label.setColor(reached ? Color.WHITE : Color.GRAY);
    label.pack();
    label.setPosition(size + 6, (size - label.getHeight()) / 2);
    node.addActor(label);
    node.setTouchable(Touchable.enabled);
    node.addListener(
        new ClickListener() {
          @Override
          public void clicked(InputEvent event, float x, float y) {
            if (reached) {
              onChoose.accept(wave);
            }
          }

          @Override
          public void enter(
              InputEvent event,
              float x,
              float y,
              int pointer,
              com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
            super.enter(event, x, y, pointer, fromActor);
            hint.setText(reached ? texts.format("map.go", wave) : texts.get("map.locked"));
          }
        });
    return node;
  }
}
