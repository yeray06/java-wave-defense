package io.github.yeray06.wavedefense.game.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.Gdx;
import io.github.yeray06.wavedefense.game.GameTestData;
import io.github.yeray06.wavedefense.game.Headless;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.data.GameData;
import org.junit.jupiter.api.Test;

class ScriptTextsTest {
  @Test
  void compileErrorsEnemiesAndShopAnswersReadInSpanish() {
    Headless.run(
        () -> {
          GameData data = GameTestData.load();
          ScriptTexts names =
              new ScriptTexts(Texts.load(Gdx.files.internal("i18n/messages")), data);
          var locked = data.compiler().compile("while (true) disparar();", Unlocks.of(1), 12);
          assertEquals(
              "Aún no has desbloqueado «Repetir (while)»", names.error(locked.errors().get(0)));
          var cut = data.compiler().compile("disparar()", Unlocks.of(1), 12);
          assertEquals("Se esperaba ; y hay el final del código", names.error(cut.errors().get(0)));
          assertEquals(
              "Se cura si pasa 1 s sin recibir daño.", names.enemyInfo(EnemyKind.REGENERATOR));
          assertEquals(
              "La munición normal le hace un 80 % menos de daño; la perforante, todo.",
              names.enemyInfo(EnemyKind.ARMORED));
          assertEquals(
              "Llega en grupos de 10, rápidos y frágiles.", names.enemyInfo(EnemyKind.SWARM));
        });
  }
}
