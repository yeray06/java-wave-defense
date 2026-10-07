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
import io.github.yeray06.wavedefense.sim.data.Challenge;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.WaveTable;
import io.github.yeray06.wavedefense.sim.progress.Goal;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import java.util.EnumMap;
import java.util.Map;

/**
 * The wave briefing (plan, "Parte de oleada"): which enemies come and how many, before they leave,
 * the wave's three stars with its code challenge (D-57), and the button that deploys the wave.
 */
public final class BriefingPanel extends Table {
  private static final float SWATCH = 16;

  private final GameData data;
  private final Progress progress;
  private final ScriptTexts names;
  private final Skin skin;
  private final TextButton deploy;
  private int shownWave = -1;
  private int shownStars = -1;

  public BriefingPanel(
      GameData data, Progress progress, ScriptTexts names, Skin skin, Runnable onDeploy) {
    this.data = data;
    this.progress = progress;
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

  /** Shows wave {@code wave}; rebuilt only when the wave or the stars earned change. */
  public void show(int wave) {
    if (wave == shownWave && progress.starsEarned() == shownStars) {
      return;
    }
    shownWave = wave;
    shownStars = progress.starsEarned();
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
    add(new Label(names.texts().get("briefing.goals"), skin)).colspan(2).left().padTop(12).row();
    Challenge challenge = data.waves().wave(wave).challenge();
    for (Goal goal : Goal.values()) {
      boolean met = progress.met(wave, goal);
      Label line = new Label(names.goalLine(goal, challenge, met), skin, met ? "good" : "default");
      line.setWrap(true);
      add(line).colspan(2).left().width(390).padLeft(10).row();
    }
    deploy.setText(names.texts().format("hud.deploy", wave));
    add(deploy).colspan(2).padTop(14);
    pack();
  }
}
