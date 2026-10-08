package io.github.yeray06.wavedefense.sim.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.api.ApiFunction;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Requirement;
import io.github.yeray06.wavedefense.sim.data.ScriptApiLoader.ScriptApiData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScriptApiLoaderTest {

  static String realFile() throws IOException {
    return Files.readString(Path.of(ScriptApiLoader.FILE));
  }

  @Test
  void loadsTheShippedFile() throws IOException {
    ScriptApiData data = ScriptApiLoader.load(realFile());
    assertEquals(1000, data.limits().maxLoopIterations(), "the plan's 1,000 iterations");
    assertEquals("disparar", data.api().name(ApiFunction.FIRE));
    assertEquals("Enemigo", data.api().enemyTypeName());
    assertEquals("PERFORANTE", data.api().ammoName(AmmoType.PIERCING));
  }

  @Test
  void shippedUnlocksMatchThePlan() throws IOException {
    ScriptApiData data = ScriptApiLoader.load(realFile());
    // Plan, "Qué programa el jugador": Inicio / Con if / Con variables / Oleada 6.
    assertEquals(new Requirement.Free(), data.api().requirement(ApiFunction.FIRE));
    assertEquals(new Requirement.Free(), data.api().requirement(ApiFunction.RELOAD));
    for (ApiFunction f :
        new ApiFunction[] {ApiFunction.AMMO, ApiFunction.HEAT, ApiFunction.HAS_ENEMIES}) {
      assertEquals(new Requirement.NeedsFeature(Feature.IF_ELSE), data.api().requirement(f));
    }
    for (ApiFunction f :
        new ApiFunction[] {
          ApiFunction.NEAREST_ENEMY,
          ApiFunction.TARGET,
          ApiFunction.AIM,
          ApiFunction.HEALTH,
          ApiFunction.SPEED,
          ApiFunction.ARMORED,
          ApiFunction.MUTANT,
          ApiFunction.DISTANCE_TO_CORE
        }) {
      assertEquals(new Requirement.NeedsFeature(Feature.VARIABLES), data.api().requirement(f));
    }
    assertEquals(new Requirement.NeedsWave(6), data.api().requirement(ApiFunction.USE_AMMO));
  }

  @Test
  void rejectsBrokenFiles() throws IOException {
    String real = realFile();
    assertRejected(real.replace("\"FIRE\"", "\"JUMP\""), "unknown call id JUMP");
    assertRejected(real.replace("\"RELOAD\"", "\"FIRE\""), "appears twice");
    assertRejected(real.replace("\"disparar\"", "\"while\""), "Reserved word");
    assertRejected(
        real.replace("\"maxLoopIterations\": 1000", "\"maxLoopIterations\": 0"), "positive");
    assertRejected(real.replace("\"maxLoopIterations\": 1000,", ""), "maxLoopIterations");
    assertRejected(real.replace("\"wave\": 6", "\"wave\": 6, \"feature\": \"WHILE\""), "both");
    assertRejected(real.replace("\"wave\": 6", "\"wave\": 0"), "wave 0");
    assertRejected(real.replace("\"IF_ELSE\" }", "\"SWITCH\" }"), "unknown feature");
    assertRejected(real.replace("\"types\"", "\"typos\""), "typos");
    assertRejected(real.replace("{ \"id\": \"PIERCING\", \"name\": \"PERFORANTE\" }", ""), "");
    assertRejected("{", "");
    assertRejected(real + "{}", "");
  }

  private static void assertRejected(String json, String messagePart) {
    DataException e = assertThrows(DataException.class, () -> ScriptApiLoader.load(json));
    assertEquals(ScriptApiLoader.FILE, e.file());
    assertTrue(e.getMessage().contains(messagePart), e.getMessage());
  }
}
