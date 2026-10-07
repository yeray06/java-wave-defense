package io.github.yeray06.wavedefense.sim.data;

import io.github.yeray06.wavedefense.script.CompileResult;
import io.github.yeray06.wavedefense.script.ScriptCompiler;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.sim.data.ScriptApiLoader.ScriptApiData;
import io.github.yeray06.wavedefense.sim.economy.Formulas;
import java.io.IOException;
import java.util.EnumSet;

/** Every data file, loaded and cross-checked. Immutable; share it freely. */
public record GameData(
    BalanceConfig balance,
    TurretConfig turret,
    EnemyCatalog enemies,
    WaveTable waves,
    ShopConfig shop,
    MapConfig map,
    ScriptApiData scriptApi) {

  public static final String BALANCE = "data/balance.json";
  public static final String TURRET = "data/turret.json";
  public static final String ENEMIES = "data/enemies.json";
  public static final String WAVES = "data/waves.json";
  public static final String SHOP = "data/shop.json";
  public static final String MAP = "data/map.json";

  /**
   * @throws DataException naming the file and the problem
   */
  public static GameData load(DataSource source) {
    GameData data =
        new GameData(
            Json.readStrict(read(source, BALANCE), BalanceConfig.class, BALANCE),
            Json.readStrict(read(source, TURRET), TurretConfig.class, TURRET),
            Json.readStrict(read(source, ENEMIES), EnemyCatalog.class, ENEMIES),
            Json.readStrict(read(source, WAVES), WaveTable.class, WAVES),
            Json.readStrict(read(source, SHOP), ShopConfig.class, SHOP),
            Json.readStrict(read(source, MAP), MapConfig.class, MAP),
            ScriptApiLoader.load(read(source, ScriptApiLoader.FILE)));
    data.crossCheck();
    return data;
  }

  /** The same data with the shop's hardware caps removed, for "sin tope" comparisons. */
  public GameData withoutHardwareCaps() {
    return new GameData(balance, turret, enemies, waves, shop.withoutCaps(), map, scriptApi);
  }

  public ScriptCompiler compiler() {
    return new ScriptCompiler(scriptApi.api(), scriptApi.limits());
  }

  private static String read(DataSource source, String file) {
    try {
      return source.read(file);
    } catch (IOException e) {
      throw new DataException(file, "cannot read: " + e.getMessage(), e);
    }
  }

  /** Rules that span several files. */
  private void crossCheck() {
    if (waves.waves().size() != balance.lastWave()) {
      throw new DataException(
          BALANCE,
          "lastWave is " + balance.lastWave() + " but waves.json lists " + waves.waves().size());
    }
    Formulas formulas = new Formulas(balance);
    for (WaveTable.WaveSpec wave : waves.waves()) {
      int expected = formulas.enemyCount(wave.wave());
      if (wave.normalEnemies() != expected) {
        throw new DataException(
            WAVES,
            "wave "
                + wave.wave()
                + " has "
                + wave.normalEnemies()
                + " enemies, formula says "
                + expected);
      }
      boolean bossWave = wave.wave() % balance.boss().everyWaves() == 0;
      long bosses =
          wave.groups().stream()
              .filter(g -> g.type() == EnemyKind.BOSS)
              .mapToInt(WaveTable.Group::count)
              .sum();
      if (bosses != (bossWave ? 1 : 0)) {
        throw new DataException(
            WAVES, "wave " + wave.wave() + " must have " + (bossWave ? "one boss" : "no boss"));
      }
      for (WaveTable.Group group : wave.groups()) {
        int size = enemies.type(group.type()).groupSize();
        if (group.count() % size != 0) {
          throw new DataException(
              WAVES, "wave " + wave.wave() + ": " + group.type() + " comes in groups of " + size);
        }
      }
    }
    if (balance.startingTurrets() > map.turretSlots().size()) {
      throw new DataException(BALANCE, "startingTurrets exceeds the map's turret slots");
    }
    for (ShopConfig.HardwareItem item : shop.hardware()) {
      for (ShopConfig.LevelCap cap : item.caps()) {
        if (item.inMvp() && !shop.syntax(cap.untilFeature()).inMvp()) {
          throw new DataException(
              SHOP, item.kind() + " is capped until " + cap.untilFeature() + ", outside the MVP");
        }
      }
    }
    for (ShopConfig.SyntaxItem item : shop.syntax()) {
      if (item.requires() != null && item.inMvp() && !shop.syntax(item.requires()).inMvp()) {
        throw new DataException(SHOP, item.feature() + " is in the MVP but requires a later piece");
      }
    }
    EnumSet<Feature> owned = EnumSet.noneOf(Feature.class);
    for (ShopConfig.SyntaxItem item : shop.syntax()) {
      if (!item.inMvp()) {
        continue;
      }
      owned.add(item.feature());
      if (item.example() == null || item.example().lines().count() > shop.exampleLines()) {
        throw new DataException(
            SHOP,
            item.feature() + " needs an example of at most " + shop.exampleLines() + " lines");
      }
      CompileResult example =
          compiler().compile(item.example(), new Unlocks(owned, 1), turret.memoryLines());
      if (!example.succeeded()) {
        throw new DataException(
            SHOP, item.feature() + " example does not compile: " + example.errors());
      }
    }
    CompileResult initial =
        compiler().compile(turret.initialScript(), Unlocks.of(1), turret.memoryLines());
    if (!initial.succeeded()) {
      throw new DataException(
          TURRET, "initialScript does not compile with nothing unlocked: " + initial.errors());
    }
  }
}
