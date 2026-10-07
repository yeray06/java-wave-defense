package io.github.yeray06.wavedefense.game.panel;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.yeray06.wavedefense.game.i18n.ScriptTexts;
import io.github.yeray06.wavedefense.game.render.EnemyPalette;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.WaveTable;
import java.util.EnumMap;
import java.util.Map;

/**
 * The wave briefing (plan, "Parte de oleada"): which enemies come and how many, before they leave,
 * with the button that deploys the wave.
 */
public final class BriefingPanel extends Table {
  private static final float SWATCH = 16;

  private final GameData data;
  private final ScriptTexts names;
  private final Skin skin;
  private final TextButton deploy;
  private int shownWave = -1;

  public BriefingPanel(GameData data, ScriptTexts names, Skin skin, Runnable onDeploy) {
    this.data = data;
    this.names = names;
    this.skin = skin;
    setBackground(skin.getDrawable("panel"));
    pad(16);
    deploy = new TextButton("", skin);
    deploy.pad(10, 24, 10, 24);
    deploy.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            onDeploy.run();
          }
        });
  }

  /** Shows wave {@code wave}; rebuilt only when the wave changes. */
  public void show(int wave) {
    if (wave == shownWave) {
      return;
    }
    shownWave = wave;
    clearChildren();
    add(new Label(names.texts().format("briefing.title", wave), skin, "title"))
        .colspan(2)
        .left()
        .padBottom(10)
        .row();
    Map<EnemyKind, Integer> counts = new EnumMap<>(EnemyKind.class);
    int total = 0;
    for (WaveTable.Group group : data.waves().wave(wave).groups()) {
      counts.merge(group.type(), group.count(), Integer::sum);
      total += group.count();
    }
    for (Map.Entry<EnemyKind, Integer> entry : counts.entrySet()) {
      Image swatch = new Image(skin.getDrawable("circle"));
      swatch.setColor(EnemyPalette.color(entry.getKey()));
      add(swatch).size(SWATCH).padRight(10).top().padTop(3);
      Table text = new Table();
      text.add(
              new Label(
                  names
                      .texts()
                      .format("briefing.group", entry.getValue(), names.enemyName(entry.getKey())),
                  skin))
          .left()
          .row();
      Label info = new Label(names.enemyInfo(entry.getKey()), skin, "small");
      info.setWrap(true);
      text.add(info).left().width(360);
      add(text).left().padBottom(8).row();
    }
    add(new Label(
            names.texts().format("briefing.total", total, data.balance().coreLivesPerWave()),
            skin,
            "small"))
        .colspan(2)
        .left()
        .padTop(4)
        .row();
    deploy.setText(names.texts().format("hud.deploy", wave));
    add(deploy).colspan(2).padTop(14);
    pack();
  }
}
