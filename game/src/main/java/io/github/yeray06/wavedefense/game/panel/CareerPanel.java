package io.github.yeray06.wavedefense.game.panel;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.yeray06.wavedefense.game.i18n.ScriptTexts;
import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.game.ui.UiSkin;
import io.github.yeray06.wavedefense.sim.data.CareerConfig;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.progress.Career;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;

/**
 * «Logros» (D-63): the streak and its score, every achievement with how far the game is towards it,
 * and the «Taller», where each turret gets a name and one of the hats its achievements gave.
 * Nothing here costs stars.
 */
public final class CareerPanel extends Table {
  private static final float WIDTH = 470;
  private static final float TEXT_WIDTH = WIDTH - 40;

  private final GameData data;
  private final WaveLoop loop;
  private final Texts texts;
  private final Skin skin;
  private long shown = -1;

  public CareerPanel(GameData data, WaveLoop loop, ScriptTexts names, Skin skin) {
    this.data = data;
    this.loop = loop;
    this.texts = names.texts();
    this.skin = skin;
    top().left();
  }

  @Override
  public void act(float delta) {
    super.act(delta);
    long state = loop.careerChanges() * 31 + loop.progress().turretCount();
    if (state != shown) {
      rebuild();
    }
  }

  /** Rebuilds the list and the workshop. */
  public void rebuild() {
    ScrollPane scroll = getParent() instanceof ScrollPane pane ? pane : null;
    float scrolledTo = scroll == null ? 0 : scroll.getScrollY();
    shown = loop.careerChanges() * 31 + loop.progress().turretCount();
    Career career = loop.career();
    clearChildren();
    top().left();
    add(new Label(texts.get("career.title"), skin, "title")).left().row();
    if (career.expert()) {
      add(wrapped(texts.get("career.expert"), "small")).left().width(TEXT_WIDTH).row();
    }
    add(wrapped(
            texts.format(
                "career.streak",
                career.streak(),
                career.streakScore(),
                career.bestStreak(),
                career.bestStreakScore()),
            "body"))
        .left()
        .width(TEXT_WIDTH)
        .padTop(4)
        .row();
    add(wrapped(texts.get("career.streak.rule"), "small")).left().width(TEXT_WIDTH).row();
    int unlocked = career.achievements().size();
    add(new Label(
            texts.format("career.count", unlocked, data.career().achievements().size()),
            skin,
            "small"))
        .left()
        .padTop(8)
        .row();
    for (CareerConfig.Achievement a : data.career().achievements()) {
      add(achievementRow(career, a)).left().width(WIDTH - 10).padTop(4).row();
    }
    add(new Label(texts.get("career.workshop"), skin, "title")).left().padTop(14).row();
    add(wrapped(texts.get("career.workshop.hint"), "small")).left().width(TEXT_WIDTH).row();
    for (int slot = 0; slot < loop.progress().turretCount(); slot++) {
      add(workshopRow(career, slot)).left().width(WIDTH - 10).padTop(6).row();
    }
    if (scroll != null) {
      scroll.layout();
      scroll.setScrollY(scrolledTo);
    }
  }

  private Table achievementRow(Career career, CareerConfig.Achievement a) {
    boolean done = career.unlocked(a.id());
    Table row = new Table();
    row.setBackground(skin.getDrawable("row"));
    row.pad(6);
    Image icon = new Image(skin.getDrawable(done ? "circle" : "ring"));
    icon.setColor(done ? UiSkin.GOOD : UiSkin.MUTED);
    row.add(icon).size(16).top().padRight(8).padTop(3);
    Table text = new Table();
    text.add(
            new Label(
                texts.get("achievement." + a.id() + ".name"), skin, done ? "good" : "default"))
        .left()
        .row();
    text.add(
            wrapped(
                texts.format("achievement." + a.id() + ".description", career.target(a)), "small"))
        .left()
        .width(TEXT_WIDTH - 30)
        .row();
    String progress =
        done
            ? texts.get("career.done")
            : texts.format(
                "career.progress",
                Math.min(career.count(a, loop.progress()), career.target(a)),
                career.target(a));
    if (a.hat() != null) {
      progress += " · " + texts.format("career.hat", texts.get("hat." + a.hat()));
    }
    text.add(new Label(progress, skin, "small")).left();
    row.add(text).growX().left();
    return row;
  }

  private Table workshopRow(Career career, int slot) {
    Table row = new Table();
    row.setBackground(skin.getDrawable("row"));
    row.pad(6);
    TextField name = new TextField(career.name(slot), skin);
    name.setMessageText(texts.format("editor.turret", slot + 1));
    name.setMaxLength(data.career().turretNameMaxLength());
    name.setTextFieldListener(
        (field, c) -> {
          String kept = loop.setTurretName(slot, field.getText());
          if (c == '\r' || c == '\n') {
            field.setText(kept);
            if (getStage() != null) {
              getStage().setKeyboardFocus(null);
            }
          }
        });
    row.add(name).width(220).left().row();
    Table hats = new Table();
    ButtonGroup<TextButton> group = new ButtonGroup<>();
    group.setMinCheckCount(0);
    hats.add(hatButton(group, slot, "", texts.get("career.noHat"), null)).left().padRight(4);
    int inRow = 1;
    for (CareerConfig.Achievement a : data.career().achievements()) {
      if (a.hat() == null || !career.unlocked(a.id())) {
        continue;
      }
      if (inRow++ % 3 == 0) {
        hats.row();
      }
      hats.add(
              hatButton(
                  group, slot, a.id(), texts.get("hat." + a.hat()), Color.valueOf(a.hatColor())))
          .left()
          .padRight(4)
          .padTop(4);
    }
    row.add(hats).left().padTop(4);
    return row;
  }

  private TextButton hatButton(
      ButtonGroup<TextButton> group, int slot, String hat, String label, Color color) {
    TextButton button = new TextButton(label, skin);
    button.pad(3, 8, 3, 8);
    if (color != null) {
      button.getLabel().setColor(color);
    }
    button.setChecked(loop.career().hat(slot).equals(hat));
    group.add(button);
    button.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            if (button.isChecked()) {
              loop.setTurretHat(slot, hat);
            }
          }
        });
    return button;
  }

  private Label wrapped(String text, String style) {
    Label label = new Label(text, skin, style);
    label.setWrap(true);
    return label;
  }
}
