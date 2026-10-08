package io.github.yeray06.wavedefense.game.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

/**
 * A window the player moves by its title, resizes from its edges, folds to its title bar and closes
 * (D-67): code popped out of the editor, a manual page, the output, the debugger. Several can be
 * open at once over the battlefield.
 */
public final class FloatingWindow extends Window {
  private static final float BUTTON_PAD = 6;
  private static final float TITLE_HEIGHT = 34;

  private final Actor content;
  private final Cell<Actor> contentCell;
  private final java.util.List<Runnable> onClose = new java.util.ArrayList<>();
  private boolean closed;
  private boolean folded;
  private float unfoldedHeight;

  public FloatingWindow(String title, Skin skin, Actor content, float width, float height) {
    super(title, skin, "floating");
    this.content = content;
    setModal(false);
    setMovable(true);
    setResizable(true);
    setResizeBorder(10);
    setKeepWithinStage(true);
    // The title bar's height: the background has none of its own.
    pad(TITLE_HEIGHT, 8, 8, 8);
    getTitleLabel().setEllipsis(true);
    TextButton fold = new TextButton("–", skin);
    fold.pad(0, BUTTON_PAD, 0, BUTTON_PAD);
    fold.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            fold.setChecked(false);
            setFolded(!folded);
          }
        });
    TextButton close = new TextButton("×", skin);
    close.pad(0, BUTTON_PAD, 0, BUTTON_PAD);
    close.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            close();
          }
        });
    getTitleTable().add(fold).padLeft(4);
    getTitleTable().add(close).padLeft(4);
    contentCell = add(content).grow();
    setSize(width, height);
  }

  /** Called once when the window closes; several listeners run in the order added. */
  public FloatingWindow onClose(Runnable listener) {
    onClose.add(listener);
    return this;
  }

  public Actor content() {
    return content;
  }

  public boolean folded() {
    return folded;
  }

  /** Folds the window to its title bar, keeping its top where it is, or unfolds it. */
  public void setFolded(boolean fold) {
    if (fold == folded) {
      return;
    }
    folded = fold;
    float top = getY() + getHeight();
    if (fold) {
      unfoldedHeight = getHeight();
      contentCell.setActor(null);
      setResizable(false);
      setHeight(getPadTop() + 4);
    } else {
      contentCell.setActor(content);
      setResizable(true);
      setHeight(unfoldedHeight);
    }
    setY(top - getHeight());
    invalidate();
  }

  /** Removes the window and tells its owner. */
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    remove();
    for (Runnable listener : onClose) {
      listener.run();
    }
  }
}
