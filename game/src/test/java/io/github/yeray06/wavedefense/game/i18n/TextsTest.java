package io.github.yeray06.wavedefense.game.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.Gdx;
import io.github.yeray06.wavedefense.game.Headless;
import org.junit.jupiter.api.Test;

/** The battle screen's texts exist and format as the player will read them. */
class TextsTest {
  @Test
  void formatsTheHudInSpanish() {
    Headless.run(
        () -> {
          Texts texts = Texts.load(Gdx.files.internal("i18n/messages"));
          assertEquals("Oleada 3/10", texts.format("hud.wave", 3, 10));
          assertEquals("×2", texts.format("hud.speed", 2));
          assertEquals("Núcleo: 7/10", texts.format("hud.core", 7, 10));
          assertEquals("Estrellas: 12 · Trofeos: 1", texts.format("hud.stars", 12, 1));
          assertEquals("Oleada 4 perdida: vuelves a la 3", texts.format("hud.result.lost", 4, 3));
          assertEquals(
              "SinMunicionException (línea 1)",
              texts.format("hud.turret.fault", texts.get("script.fault.NO_AMMO"), 1));
          for (String key :
              new String[] {
                "hud.auto.on",
                "hud.auto.off",
                "hud.deploy",
                "hud.result.perfect",
                "hud.result.won",
                "hud.hint.reload",
                "hud.turret.reloading",
                "hud.turret.empty"
              }) {
            texts.get(key);
          }
        });
  }
}
