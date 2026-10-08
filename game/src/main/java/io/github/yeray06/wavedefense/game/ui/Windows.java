package io.github.yeray06.wavedefense.game.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The floating windows over the battlefield (D-67), one per key: opening a key already open brings
 * its window to the front instead of making another. New windows cascade from the top left so they
 * do not cover each other exactly.
 */
public final class Windows {
  private static final float CASCADE = 28;
  private static final float MARGIN = 120;

  private final Stage stage;
  private final Skin skin;
  private final Map<String, FloatingWindow> open = new LinkedHashMap<>();
  private int opened;

  public Windows(Stage stage, Skin skin) {
    this.stage = stage;
    this.skin = skin;
  }

  /**
   * Opens the window for {@code key}, or brings it to the front.
   *
   * @param content made only if the window is not open yet
   * @return the window
   */
  public FloatingWindow open(
      String key, String title, Supplier<Actor> content, float width, float height) {
    FloatingWindow window = open.get(key);
    if (window != null) {
      window.setFolded(false);
      window.toFront();
      return window;
    }
    FloatingWindow created = new FloatingWindow(title, skin, content.get(), width, height);
    float step = (opened++ % 8) * CASCADE;
    created.setPosition(
        Math.min(MARGIN + step, Math.max(0, stage.getWidth() - width)),
        Math.max(0, stage.getHeight() - MARGIN - height - step));
    created.onClose(() -> open.remove(key));
    open.put(key, created);
    stage.addActor(created);
    return created;
  }

  /** The window open for {@code key}, or {@code null}. */
  public FloatingWindow get(String key) {
    return open.get(key);
  }

  public boolean isOpen(String key) {
    return open.containsKey(key);
  }

  /** Closes the window for {@code key}, if open. */
  public void close(String key) {
    FloatingWindow window = open.get(key);
    if (window != null) {
      window.close();
    }
  }
}
