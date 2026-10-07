package io.github.yeray06.wavedefense.game.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.yeray06.wavedefense.game.Headless;
import java.util.List;
import org.junit.jupiter.api.Test;

/** D-47: the speed control opens on hover, picks a speed on click and respects the wave's rule. */
class SpeedSelectorTest {
  @Test
  void opensOnHoverPicksASpeedAndFallsBackToOneWhenTheWaveForbidsIt() {
    Headless.run(
        () -> {
          Skin skin = UiSkin.create(Gdx.files.internal("fonts/JetBrainsMono-Regular.ttf"));
          SpeedSelector selector = new SpeedSelector(List.of(1, 2, 3), skin, s -> "×" + s, 8);
          assertEquals(1, selector.getChildren().size, "closed: only the speed in use");
          assertEquals(1, selector.speed());

          hover(selector, InputEvent.Type.enter);
          assertTrue(selector.isOpen());
          assertEquals(3, selector.getChildren().size, "open: one button per speed");

          selector.button(2).fire(new ChangeListener.ChangeEvent());
          assertEquals(3, selector.speed());
          hover(selector, InputEvent.Type.exit);
          assertFalse(selector.isOpen());
          assertEquals(1, selector.getChildren().size);
          assertEquals("×3", selector.button(2).getText().toString());

          selector.setFastAllowed(false);
          assertEquals(1, selector.speed(), "a wave not yet beaten without leaks runs at ×1");
          assertEquals(3, selector.requestedSpeed(), "the pick is kept (D-43)");
          assertTrue(selector.button(1).isDisabled() && selector.button(2).isDisabled());
          assertFalse(selector.button(0).isDisabled());
          selector.setFastAllowed(true);
          assertEquals(3, selector.speed());
          skin.dispose();
        });
  }

  private static void hover(SpeedSelector selector, InputEvent.Type type) {
    InputEvent event = new InputEvent();
    event.setType(type);
    event.setPointer(-1);
    event.setRelatedActor(null);
    selector.fire(event);
  }
}
