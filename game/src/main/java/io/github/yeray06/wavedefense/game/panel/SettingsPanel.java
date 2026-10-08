package io.github.yeray06.wavedefense.game.panel;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.game.screen.GameControls;
import io.github.yeray06.wavedefense.game.settings.Settings;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * The settings (phase 6, D-56): full screen, size of the interface and of the code, and a new game
 * after a confirmation. The game saves itself, so there is no save button; the panel says where.
 */
public final class SettingsPanel extends Table {
  private static final float WIDTH = 470;

  private final GameControls controls;
  private final Texts texts;
  private final Skin skin;

  public SettingsPanel(GameControls controls, Texts texts, Skin skin) {
    this.controls = controls;
    this.texts = texts;
    this.skin = skin;
    rebuild();
  }

  /** Redraws the current values. */
  public void rebuild() {
    clearChildren();
    top().left();
    Settings settings = controls.settings();

    add(new Label(texts.get("settings.screen"), skin, "title")).left().row();
    TextButton fullscreen =
        new TextButton(
            texts.get(settings.fullscreen() ? "settings.fullscreen.on" : "settings.fullscreen.off"),
            skin);
    fullscreen.pad(4, 12, 4, 12);
    fullscreen.setChecked(settings.fullscreen());
    fullscreen.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            controls.apply(controls.settings().withFullscreen(!controls.settings().fullscreen()));
            rebuild();
          }
        });
    add(fullscreen).left().padTop(4).row();

    add(new Label(texts.get("settings.uiScale"), skin)).left().padTop(12).row();
    add(choices(
            Settings.UI_SCALES,
            settings.uiScalePercent(),
            percent -> texts.format("settings.percent", percent),
            percent -> controls.apply(controls.settings().withUiScale(percent))))
        .left()
        .padTop(4)
        .row();

    add(new Label(texts.get("settings.codeSize"), skin)).left().padTop(12).row();
    add(choices(
            Settings.CODE_SIZES,
            settings.codeFontSize(),
            pixels -> String.valueOf(pixels),
            pixels -> controls.apply(controls.settings().withCodeFontSize(pixels))))
        .left()
        .padTop(4)
        .row();
    add(wrapped(texts.get("settings.sizes.note"), "small")).left().width(WIDTH).padTop(4).row();

    add(new Label(texts.get("settings.game"), skin, "title")).left().padTop(18).row();
    add(wrapped(texts.format("settings.saved", controls.saveFolder().toString()), "small"))
        .left()
        .width(WIDTH)
        .padTop(4)
        .row();
    TextButton newGame = new TextButton(texts.get("settings.newGame"), skin);
    newGame.pad(4, 12, 4, 12);
    newGame.setProgrammaticChangeEvents(false);
    newGame.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            newGame.setChecked(false);
            confirmNewGame(false);
          }
        });
    add(newGame).left().padTop(6).row();
    TextButton expertGame = new TextButton(texts.get("settings.expertGame"), skin);
    expertGame.pad(4, 12, 4, 12);
    expertGame.setProgrammaticChangeEvents(false);
    expertGame.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            expertGame.setChecked(false);
            confirmNewGame(true);
          }
        });
    add(expertGame).left().padTop(6).row();
    add(wrapped(texts.get("settings.expertGame.hint"), "small"))
        .left()
        .width(WIDTH)
        .padTop(2)
        .row();
  }

  private Table choices(
      List<Integer> values, int current, IntFunction<String> label, IntConsumer pick) {
    Table row = new Table();
    ButtonGroup<TextButton> group = new ButtonGroup<>();
    for (int value : values) {
      TextButton button = new TextButton(label.apply(value), skin);
      button.pad(4, 8, 4, 8);
      button.setChecked(value == current);
      group.add(button);
      button.addListener(
          new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
              if (button.isChecked() && value != current) {
                pick.accept(value);
              }
            }
          });
      row.add(button).padRight(4);
    }
    return row;
  }

  private void confirmNewGame(boolean expert) {
    if (getStage() == null) {
      return;
    }
    Dialog dialog =
        new Dialog(texts.get("settings.newGame.title"), skin) {
          @Override
          protected void result(Object start) {
            if (Boolean.TRUE.equals(start)) {
              controls.newGame(expert);
            }
          }
        };
    dialog.pad(40, 20, 16, 20);
    String text = expert ? "settings.expertGame.text" : "settings.newGame.text";
    dialog.getContentTable().add(wrapped(texts.get(text), "default")).width(420);
    TextButton yes = new TextButton(texts.get("settings.newGame.yes"), skin);
    yes.pad(6, 16, 6, 16);
    TextButton no = new TextButton(texts.get("settings.newGame.no"), skin);
    no.pad(6, 16, 6, 16);
    dialog.button(no, false);
    dialog.button(yes, true);
    dialog.show(getStage());
  }

  private Label wrapped(String text, String style) {
    Label label = new Label(text, skin, style);
    label.setWrap(true);
    return label;
  }
}
