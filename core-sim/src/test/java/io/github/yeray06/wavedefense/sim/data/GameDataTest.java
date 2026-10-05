package io.github.yeray06.wavedefense.sim.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.sim.TestData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GameDataTest {

  @Test
  void loadsEveryShippedFile() {
    GameData data = TestData.load();
    assertEquals(10, data.balance().lastWave());
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
    assertRejected(GameData.BALANCE, "\"lastWave\": 10", "\"lastWave\": 9", "lastWave");
    assertRejected(GameData.BALANCE, "\"startingTurrets\": 1", "\"startingTurrets\": 3", "slots");
    assertRejected(
        GameData.SHOP,
        "{ \"kind\": \"DAMAGE\", \"firstCost\": 20, \"costGrowth\": 1.6, \"effectPerLevel\": 0.25, \"inMvp\": true },",
        "",
        "DAMAGE");
    assertRejected(
        GameData.MAP, "{ \"x\": 1000, \"y\": 560 },", "{ \"x\": 0, \"y\": 560 },", "zero-length");
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
