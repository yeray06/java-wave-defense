package io.github.yeray06.wavedefense.game.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import java.util.List;
import java.util.function.IntFunction;

/**
 * The game-speed control (D-47): closed, it shows the speed in use; with the mouse over it, it
 * opens sideways into one button per speed (×1, ×2, ×3) and a click picks one. Speeds above the
 * first are disabled while the wave does not allow them; the player's pick is kept for when it does
 * (D-43).
 */
public final class SpeedSelector extends Table {
  private final List<Integer> speeds;
  private final TextButton[] buttons;
  private int requested;
  private boolean fastAllowed = true;
  private boolean open;

  /**
   * @param speeds the speeds from data, starting at 1
   * @param label the text for a speed, e.g. "×2"
   */
  public SpeedSelector(List<Integer> speeds, Skin skin, IntFunction<String> label, float pad) {
    this.speeds = List.copyOf(speeds);
    this.buttons = new TextButton[speeds.size()];
    for (int i = 0; i < buttons.length; i++) {
      int index = i;
      TextButton button = new TextButton(label.apply(speeds.get(i)), skin);
      button.pad(4, pad, 4, pad);
      button.setProgrammaticChangeEvents(false);
      button.addListener(
          new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
              requested = index;
              refresh();
            }
          });
      buttons[i] = button;
    }
    addListener(
        new InputListener() {
          @Override
          public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
            if (pointer == -1
                && (fromActor == null || !fromActor.isDescendantOf(SpeedSelector.this))) {
              setOpen(true);
            }
          }

          @Override
          public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
            if (pointer == -1 && (toActor == null || !toActor.isDescendantOf(SpeedSelector.this))) {
              setOpen(false);
            }
          }
        });
    refresh();
  }

  /** The speed the player picked, whether or not the wave allows it now. */
  public int requestedSpeed() {
    return speeds.get(requested);
  }

  /** The speed to run at: the pick, or the first one while the wave does not allow more. */
  public int speed() {
    return fastAllowed ? speeds.get(requested) : speeds.get(0);
  }

  /** Whether the current wave allows speeds above the first. */
  public void setFastAllowed(boolean allowed) {
    if (allowed != fastAllowed) {
      fastAllowed = allowed;
      refresh();
    }
  }

  public boolean isOpen() {
    return open;
  }

  /** Opens or closes the row; the mouse does this through enter and exit. */
  public void setOpen(boolean open) {
    if (open != this.open) {
      this.open = open;
      refresh();
    }
  }

  private void refresh() {
    int shown = fastAllowed ? requested : 0;
    clearChildren();
    for (int i = 0; i < buttons.length; i++) {
      buttons[i].setChecked(i == shown);
      buttons[i].setDisabled(i > 0 && !fastAllowed);
      if (open || i == shown) {
        add(buttons[i]).minWidth(56);
      }
    }
    invalidateHierarchy();
  }

  /** The button for {@code speed}, for tests and tooltips. */
  TextButton button(int index) {
    return buttons[index];
  }
}
