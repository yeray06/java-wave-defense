package io.github.yeray06.wavedefense.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.api.ApiFunction;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Requirement;
import io.github.yeray06.wavedefense.script.api.ScriptApi;
import io.github.yeray06.wavedefense.script.api.ScriptApi.FunctionSpec;
import io.github.yeray06.wavedefense.script.api.ScriptLimits;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The vocabulary is data: renaming works, and bad data is rejected up front. */
class ScriptApiTest {

  @Test
  void everyNameComesFromTheVocabulary() {
    Map<ApiFunction, FunctionSpec> functions = TestApi.functions();
    functions.put(ApiFunction.FIRE, new FunctionSpec("shoot", new Requirement.Free()));
    Map<AmmoType, String> ammo = TestApi.ammoNames();
    ammo.put(AmmoType.PIERCING, "AP");
    ScriptApi english = new ScriptApi("Bug", "Ammo", functions, ammo);
    ScriptCompiler compiler = new ScriptCompiler(english, TestApi.LIMITS);

    assertTrue(
        compiler
            .compile("shoot();\nusarMunicion(AP);\nBug b = null;", Scripts.ALL, 12)
            .succeeded());
    assertTrue(!compiler.compile("disparar();", Scripts.ALL, 12).succeeded());
  }

  @Test
  void rejectsMissingCalls() {
    Map<ApiFunction, FunctionSpec> functions = TestApi.functions();
    functions.remove(ApiFunction.AIM);
    assertThrows(
        IllegalArgumentException.class,
        () -> new ScriptApi("Enemigo", "Municion", functions, TestApi.ammoNames()));
  }

  @Test
  void rejectsReservedWordsInvalidIdentifiersAndDuplicates() {
    assertRejected(ApiFunction.FIRE, "while");
    assertRejected(ApiFunction.FIRE, "dis parar");
    assertRejected(ApiFunction.FIRE, "1disparar");
    assertRejected(ApiFunction.FIRE, "");
    assertRejected(ApiFunction.FIRE, "recargar");
    assertThrows(
        IllegalArgumentException.class,
        () -> new ScriptApi("int", "Municion", TestApi.functions(), TestApi.ammoNames()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ScriptApi("Enemigo", "Enemigo", TestApi.functions(), TestApi.ammoNames()));
    Map<AmmoType, String> ammo = TestApi.ammoNames();
    ammo.put(AmmoType.PIERCING, "NORMAL");
    assertThrows(
        IllegalArgumentException.class,
        () -> new ScriptApi("Enemigo", "Municion", TestApi.functions(), ammo));
  }

  @Test
  void turretAndEnemyCallsAreSeparateNamespaces() {
    Map<ApiFunction, FunctionSpec> functions = TestApi.functions();
    functions.put(
        ApiFunction.HEALTH,
        new FunctionSpec("calor", new Requirement.NeedsFeature(Feature.VARIABLES)));
    ScriptApi api = new ScriptApi("Enemigo", "Municion", functions, TestApi.ammoNames());
    assertEquals(ApiFunction.HEAT, api.turretCall("calor").orElseThrow());
    assertEquals(ApiFunction.HEALTH, api.enemyCall("calor").orElseThrow());
  }

  @Test
  void limitsMustBePositive() {
    assertThrows(IllegalArgumentException.class, () -> new ScriptLimits(0, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> new ScriptLimits(1, -1, 1));
    assertThrows(IllegalArgumentException.class, () -> new Requirement.NeedsWave(0));
  }

  @Test
  void unlocksSatisfyRequirements() {
    Unlocks unlocks = Unlocks.of(6, Feature.IF_ELSE);
    assertTrue(unlocks.satisfies(new Requirement.Free()));
    assertTrue(unlocks.satisfies(new Requirement.NeedsFeature(Feature.IF_ELSE)));
    assertTrue(!unlocks.satisfies(new Requirement.NeedsFeature(Feature.WHILE)));
    assertTrue(unlocks.satisfies(new Requirement.NeedsWave(6)));
    assertTrue(!unlocks.satisfies(new Requirement.NeedsWave(7)));
  }

  private static void assertRejected(ApiFunction function, String name) {
    Map<ApiFunction, FunctionSpec> functions = TestApi.functions();
    functions.put(function, new FunctionSpec(name, new Requirement.Free()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ScriptApi("Enemigo", "Municion", functions, TestApi.ammoNames()),
        name);
  }
}
