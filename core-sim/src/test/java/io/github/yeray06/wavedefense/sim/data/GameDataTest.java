package io.github.yeray06.wavedefense.sim.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.api.ApiFunction;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.sim.TestData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GameDataTest {

  @Test
  void loadsEveryShippedFile() {
    GameData data = TestData.load();
    assertEquals(16, data.balance().lastWave());
    assertEquals(20, data.turret().heatPerShot().get(AmmoType.NORMAL));
    assertEquals(40, data.turret().heatPerShot().get(AmmoType.PIERCING));
    assertEquals(0.8, data.enemies().armorDamageReduction());
    assertTrue(data.waves().wave(10).hasBoss());
    assertFalse(data.waves().wave(9).hasBoss());
  }

  @Test
  void everyFileListsWhatDoesNotComeFromThePlan() {
    GameData data = TestData.load();
    assertTrue(data.turret().notFromPlan().contains("rangeUnits"));
    assertTrue(data.enemies().notFromPlan().contains("types.BASIC.speed"));
    assertTrue(data.waves().notFromPlan().contains("waves"));
  }

  @Test
  void enemySpeedsAreDistinctIntegersPerType() {
    // D-22: velocidad() returns the data value exactly.
    for (EnemyKind kind : EnemyKind.values()) {
      assertTrue(TestData.load().enemies().type(kind).speed() > 0);
    }
  }

  @Test
  void rejectsBrokenData() {
    assertRejected(GameData.WAVES, "\"count\": 8 }", "\"count\": 9 }", "formula says 8");
    assertRejected(
        GameData.WAVES,
        "{ \"type\": \"BOSS\", \"count\": 1 }",
        "{ \"type\": \"BASIC\", \"count\": 1 }",
        "");
    assertRejected(
        GameData.WAVES,
        "\"type\": \"SWARM\", \"count\": 10 }, { \"type\": \"ARMORED\", \"count\": 3 }",
        "\"type\": \"SWARM\", \"count\": 5 }, { \"type\": \"ARMORED\", \"count\": 8 }",
        "groups of 10");
    assertRejected(
        GameData.WAVES,
        "{ \"type\": \"MAX_MEMORY\", \"limit\": 4 }",
        "{ \"type\": \"MAX_MEMORY\", \"limit\": 40 }",
        "memory challenge");
    assertRejected(
        GameData.WAVES,
        "{ \"type\": \"NO_AIR_SHOTS\", \"limit\": 0 }",
        "{ \"type\": \"NO_AIR_SHOTS\", \"limit\": 3 }",
        "takes no limit");
    assertRejected(GameData.ENEMIES, "\"speed\": 80", "\"speed\": 0", "speed must be positive");
    assertRejected(
        GameData.ENEMIES,
        "\"armorDamageReduction\": 0.8",
        "\"armorDamageReduction\": 1.8",
        "between 0 and 1");
    assertRejected(GameData.TURRET, "\"magazine\": 12,", "", "magazine");
    assertRejected(
        GameData.TURRET, "\"magazine\": 12,", "\"magazine\": 12, \"magazin\": 3,", "magazin");
    assertRejected(
        GameData.TURRET,
        "\"initialScript\": \"disparar();\"",
        "\"initialScript\": \"while (true) disparar();\"",
        "initialScript");
    assertRejected(GameData.BALANCE, "\"lastWave\": 16", "\"lastWave\": 15", "lastWave");
    assertRejected(
        GameData.WAVES,
        "\"mutantWeakness\": \"NORMAL\"",
        "\"mutantWeakness\": null",
        "needs mutantWeakness");
    assertRejected(
        GameData.WAVES,
        "\"NO_AIR_SHOTS\", \"limit\": 0 }, \"mutantWeakness\": null",
        "\"NO_AIR_SHOTS\", \"limit\": 0 }, \"mutantWeakness\": \"NORMAL\"",
        "only for waves with Mutants");
    assertRejected(
        GameData.SHOP,
        "\"exampleInLibrary\": true",
        "\"exampleInLibrary\": false",
        "example does not compile");
    assertRejected(GameData.BALANCE, "\"startingTurrets\": 1", "\"startingTurrets\": 5", "slots");
    assertRejected(GameData.BALANCE, "\"speeds\": [1, 2, 3]", "\"speeds\": [2, 3]", "start at 1");
    assertRejected(GameData.BALANCE, "\"speeds\": [1, 2, 3]", "\"speeds\": [1, 3, 2]", "grow");
    assertRejected(GameData.SHOP, "\"kind\": \"DAMAGE\"", "\"kind\": \"MEMORY\"", "exactly once");
    assertRejected(GameData.SHOP, "\"exampleLines\": 5", "\"exampleLines\": 3", "at most 3 lines");
    assertRejected(
        GameData.SHOP,
        "while (hayEnemigos() && municion()",
        "for (hayEnemigos() && municion()",
        "example does not compile");
    assertRejected(
        GameData.SHOP,
        "\"untilFeature\": \"WHILE\"",
        "\"untilFeature\": \"TRY_CATCH\"",
        "outside the MVP");
    assertRejected(
        GameData.MAP, "{ \"x\": 1000, \"y\": 560 },", "{ \"x\": 0, \"y\": 560 },", "zero-length");
  }

  @Test
  void rejectsABrokenManual() {
    assertRejected(
        GameData.MANUAL,
        "recargar();\\ndisparar();  // nunca",
        "recargar();\\nwhile (true) disparar();  // nunca",
        "CALL_RELOAD example 1 does not compile");
    assertRejected(
        GameData.MANUAL, "\"call\": \"SPEED\"", "\"call\": \"HEALTH\"", "exactly one page");
    assertRejected(
        GameData.MANUAL, "\"fault\": \"OVERHEAT\"", "\"fault\": \"NO_AMMO\"", "exactly one page");
    assertRejected(
        GameData.MANUAL, "\"id\": \"START_GAME\"", "\"id\": \"START_SHOP\"", "used twice");
    assertRejected(
        GameData.MANUAL,
        "\"id\": \"START_GAME\"",
        "\"id\": \"START_GAME\", \"about\": { \"call\": \"FIRE\", \"feature\": \"WHILE\" }",
        "one call, piece");
  }

  @Test
  void manualPagesUnlockWithTheirCallPieceOrFirstWave() {
    GameData data = TestData.load();
    var ammo = new ManualConfig.Topic(ApiFunction.USE_AMMO, null, null, null);
    assertEquals(6, data.unlocksFor(List.of(ammo)).highestWaveReached());
    var whilePiece = new ManualConfig.Topic(null, Feature.WHILE, null, null);
    assertEquals(
        EnumSet.of(Feature.IF_ELSE, Feature.VARIABLES, Feature.LOGIC_OPS, Feature.WHILE),
        EnumSet.copyOf(data.unlocksFor(List.of(whilePiece)).features()),
        "a piece brings the ones it requires");
    var swarm = new ManualConfig.Topic(null, null, EnemyKind.SWARM, null);
    assertEquals(
        data.waves().firstWave(EnemyKind.SWARM),
        data.unlocksFor(List.of(swarm)).highestWaveReached());
    assertTrue(
        data.requirements(new ManualConfig.Topic(null, null, EnemyKind.BASIC, null)).isEmpty());
    assertTrue(data.requirements(ManualConfig.Topic.NONE).isEmpty());
  }

  @Test
  void theFullTurretRecipeFillsTheMemoryAsItsCaptionSays() {
    GameData data = TestData.load();
    ManualConfig.Page full =
        data.manual().pages().stream()
            .filter(p -> p.id().equals("RECIPE_FULL"))
            .findFirst()
            .orElseThrow();
    var result =
        data.compiler()
            .compile(
                full.examples().get(0).code(),
                data.unlocksFor(full.topics()),
                data.turret().memoryLines());
    assertEquals(data.turret().memoryLines(), result.memoryUsed());
  }

  private static void assertRejected(String file, String find, String replace, String messagePart) {
    Map<String, String> overrides;
    try {
      String original = Files.readString(Path.of(file));
      if (!original.contains(find)) {
        throw new AssertionError("test fixture out of date: " + find + " not in " + file);
      }
      overrides =
          Map.of(
              file,
              original.replaceFirst(
                  java.util.regex.Pattern.quote(find),
                  java.util.regex.Matcher.quoteReplacement(replace)));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    DataException e =
        assertThrows(
            DataException.class,
            () ->
                GameData.load(
                    path ->
                        overrides.containsKey(path)
                            ? overrides.get(path)
                            : Files.readString(Path.of(path))));
    assertEquals(file, e.file(), e.getMessage());
    assertTrue(e.getMessage().contains(messagePart), e.getMessage());
  }
}
