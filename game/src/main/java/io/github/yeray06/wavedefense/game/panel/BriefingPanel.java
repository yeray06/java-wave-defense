package io.github.yeray06.wavedefense.game.panel;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.yeray06.wavedefense.game.i18n.ScriptTexts;
import io.github.yeray06.wavedefense.game.render.EnemyPalette;
import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.sim.data.Challenge;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.WaveTable;
import io.github.yeray06.wavedefense.sim.progress.Career;
import io.github.yeray06.wavedefense.sim.progress.Goal;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.WaveRecord;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntConsumer;

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
  private int lastWave;
  private int shownStars = -1;
  private Career career;
  private IntConsumer onTrial;
  private WaveRecord shownRecord;

  /** Enemies, goals and records; they scroll in a small window, the buttons never do. */
  private final Table details = new Table();

  private final ScrollPane detailsScroll;
  private float maxHeight = Float.MAX_VALUE;

  /** Room for the deploy and trial buttons under the details. */
  private static final float BUTTONS_HEIGHT = 80;

  public BriefingPanel(
      GameData data, Progress progress, ScriptTexts names, Skin skin, Runnable onDeploy) {
    this.data = data;
    this.progress = progress;
    this.names = names;
    this.skin = skin;
    setBackground(skin.getDrawable("panel"));
    pad(16);
    detailsScroll = new ScrollPane(details, skin);
    detailsScroll.setFadeScrollBars(false);
    detailsScroll.setScrollingDisabled(true, false);
    details.padRight(14);
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

  /** The tallest the briefing may be: the space between the toolbar and the hints. */
  public void setMaxHeight(float height) {
    if (height != maxHeight) {
      maxHeight = height;
      shownWave = -1;
      if (lastWave > 0) {
        show(lastWave);
      }
    }
  }

  private float maxDetailsHeight() {
    return Math.max(120, maxHeight - BUTTONS_HEIGHT);
  }

  /** The player's records, shown under the goals (D-63). */
  public void setCareer(Career career) {
    this.career = career;
  }

  /** What «Probar» does with the wave shown (D-65); without it there is no button. */
  public void setTrial(IntConsumer onTrial) {
    this.onTrial = onTrial;
  }

  /** Shows wave {@code wave}; rebuilt only when the wave, the stars or the records change. */
  public void show(int wave) {
    WaveRecord record = career == null ? null : career.record(wave);
    if (wave == shownWave
        && progress.starsEarned() == shownStars
        && java.util.Objects.equals(record, shownRecord)) {
      return;
    }
    shownWave = wave;
    lastWave = wave;
    shownStars = progress.starsEarned();
    shownRecord = record;
    clearChildren();
    details.clearChildren();
    details
        .add(new Label(names.texts().format("briefing.title", wave), skin, "title"))
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
      details.add(swatch).size(SWATCH).padRight(10).left().top().padTop(3);
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
      AmmoType weakness = data.waves().wave(wave).mutantWeakness();
      if (entry.getKey() == EnemyKind.MUTANT && weakness != null) {
        // What changes every wave: the one thing to update in the library.
        String ammo = data.scriptApi().api().ammoName(weakness);
        text.row();
        text.add(new Label(names.texts().format("mutant.weakness", ammo), skin, "warning"))
            .left()
            .padTop(2);
      }
      details.add(text).left().expandX().padBottom(8).row();
    }
    details
        .add(
            new Label(
                names.texts().format("briefing.total", total, data.balance().coreLivesPerWave()),
                skin,
                "small"))
        .colspan(2)
        .left()
        .padTop(4)
        .row();
    details
        .add(new Label(names.texts().get("briefing.goals"), skin))
        .colspan(2)
        .left()
        .padTop(12)
        .row();
    Challenge challenge = data.waves().wave(wave).challenge();
    for (Goal goal : Goal.values()) {
      boolean met = progress.met(wave, goal);
      Label line = new Label(names.goalLine(goal, challenge, met), skin, met ? "good" : "default");
      line.setWrap(true);
      details.add(line).colspan(2).left().width(390).padLeft(10).row();
    }
    if (record != null) {
      int tps = data.balance().ticksPerSecond();
      Label records =
          new Label(
              names
                  .texts()
                  .format(
                      "briefing.records",
                      ReportLines.recordText(WaveRecord.Mark.TICKS, record, names, tps),
                      ReportLines.recordText(WaveRecord.Mark.SHOTS, record, names, tps),
                      ReportLines.recordText(WaveRecord.Mark.MEMORY, record, names, tps)),
              skin,
              "small");
      records.setWrap(true);
      details.add(records).colspan(2).left().width(390).padTop(8).row();
    }
    deploy.setText(names.texts().format("hud.deploy", wave));
    Table buttons = new Table();
    buttons.add(deploy);
    if (onTrial != null) {
      TextButton trial = new TextButton(names.texts().get("trial.button"), skin);
      trial.pad(10, 18, 10, 18);
      trial.addListener(
          new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
              trial.setChecked(false);
              onTrial.accept(shownWave);
            }
          });
      buttons.add(trial).padLeft(10);
    }
    detailsScroll.setActor(details);
    add(detailsScroll).maxHeight(maxDetailsHeight()).row();
    add(buttons).padTop(14);
    pack();
  }
}
