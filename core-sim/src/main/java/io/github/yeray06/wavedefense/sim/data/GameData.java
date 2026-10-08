package io.github.yeray06.wavedefense.sim.data;

import io.github.yeray06.wavedefense.script.CompileResult;
import io.github.yeray06.wavedefense.script.LibraryResult;
import io.github.yeray06.wavedefense.script.ScriptCompiler;
import io.github.yeray06.wavedefense.script.api.ApiFunction;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Requirement;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.script.diag.CompileError;
import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.sim.data.ScriptApiLoader.ScriptApiData;
import io.github.yeray06.wavedefense.sim.economy.Formulas;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Every data file, loaded and cross-checked. Immutable; share it freely. */
public record GameData(
    BalanceConfig balance,
    TurretConfig turret,
    EnemyCatalog enemies,
    WaveTable waves,
    ShopConfig shop,
    MapConfig map,
    ScriptApiData scriptApi,
    ManualConfig manual) {

  public static final String BALANCE = "data/balance.json";
  public static final String TURRET = "data/turret.json";
  public static final String ENEMIES = "data/enemies.json";
  public static final String WAVES = "data/waves.json";
  public static final String SHOP = "data/shop.json";
  public static final String MAP = "data/map.json";
  public static final String MANUAL = "data/manual.json";

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
            ScriptApiLoader.load(read(source, ScriptApiLoader.FILE)),
            Json.read(read(source, MANUAL), ManualConfig.class, MANUAL));
    data.crossCheck();
    return data;
  }

  /** The same data with the shop's hardware caps removed, for "sin tope" comparisons. */
  public GameData withoutHardwareCaps() {
    return new GameData(
        balance, turret, enemies, waves, shop.withoutCaps(), map, scriptApi, manual);
  }

  public ScriptCompiler compiler() {
    return new ScriptCompiler(scriptApi.api(), scriptApi.limits());
  }

  /**
   * What a manual topic asks of the player before it is theirs: the shop piece or the wave of a
   * call, the piece itself, or the first wave of an enemy. Nothing for exceptions and general
   * pages.
   */
  public List<Requirement> requirements(ManualConfig.Topic topic) {
    if (topic.call() != null) {
      Requirement requirement = scriptApi.api().requirement(topic.call());
      return requirement instanceof Requirement.Free ? List.of() : List.of(requirement);
    }
    if (topic.feature() != null) {
      return List.of(new Requirement.NeedsFeature(topic.feature()));
    }
    if (topic.enemy() != null && waves.firstWave(topic.enemy()) > 1) {
      return List.of(new Requirement.NeedsWave(waves.firstWave(topic.enemy())));
    }
    return List.of();
  }

  /**
   * The least a player owns when every topic is theirs: the pieces with the ones they require
   * first, and the latest wave. Manual examples compile with this.
   */
  public Unlocks unlocksFor(List<ManualConfig.Topic> topics) {
    EnumSet<Feature> features = EnumSet.noneOf(Feature.class);
    int wave = 1;
    for (ManualConfig.Topic topic : topics) {
      for (Requirement requirement : requirements(topic)) {
        switch (requirement) {
          case Requirement.NeedsFeature needs -> {
            for (Feature f = needs.feature(); f != null; f = shop.syntax(f).requires()) {
              features.add(f);
            }
          }
          case Requirement.NeedsWave needs -> wave = Math.max(wave, needs.wave());
          case Requirement.Free free -> {}
        }
      }
    }
    return new Unlocks(features, wave);
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
      Challenge challenge = wave.challenge();
      if (challenge.type() == Challenge.Type.MAX_MEMORY
          && challenge.limit() > turret.memoryLines()) {
        throw new DataException(
            WAVES, "wave " + wave.wave() + ": a memory challenge above the turret's memory");
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
      Unlocks unlocks = new Unlocks(owned, 1);
      List<CompileError> errors =
          item.exampleInLibrary()
              ? compiler().compileLibrary(item.example(), unlocks).errors()
              : compiler().compile(item.example(), unlocks, turret.memoryLines()).errors();
      if (!errors.isEmpty()) {
        throw new DataException(SHOP, item.feature() + " example does not compile: " + errors);
      }
    }
    checkManual();
    CompileResult initial =
        compiler().compile(turret.initialScript(), Unlocks.of(1), turret.memoryLines());
    if (!initial.succeeded()) {
      throw new DataException(
          TURRET, "initialScript does not compile with nothing unlocked: " + initial.errors());
    }
  }

  /** Every call, piece, enemy and exception has its page, and every example works as it is. */
  private void checkManual() {
    List<ManualConfig.Topic> subjects = new ArrayList<>();
    for (ApiFunction call : ApiFunction.values()) {
      subjects.add(new ManualConfig.Topic(call, null, null, null));
    }
    for (ShopConfig.SyntaxItem item : shop.syntax()) {
      if (item.inMvp()) {
        subjects.add(new ManualConfig.Topic(null, item.feature(), null, null));
      }
    }
    for (EnemyKind kind : EnemyKind.values()) {
      subjects.add(new ManualConfig.Topic(null, null, kind, null));
    }
    for (FaultType fault : FaultType.values()) {
      if (fault != FaultType.INTERNAL_ERROR) {
        subjects.add(new ManualConfig.Topic(null, null, null, fault));
      }
    }
    for (ManualConfig.Topic subject : subjects) {
      long pages = manual.pages().stream().filter(p -> p.about().equals(subject)).count();
      if (pages != 1) {
        throw new DataException(MANUAL, "needs exactly one page about " + subject + ": " + pages);
      }
    }
    for (ManualConfig.Page page : manual.pages()) {
      for (ManualConfig.Topic topic : page.topics()) {
        if (topic.feature() != null && !shop.syntax(topic.feature()).inMvp()) {
          throw new DataException(MANUAL, page.id() + " is about a piece outside the MVP");
        }
        if (topic.enemy() != null && waves.firstWave(topic.enemy()) == 0) {
          throw new DataException(MANUAL, page.id() + ": " + topic.enemy() + " is in no wave");
        }
      }
      List<ManualConfig.Topic> pageTopics = new ArrayList<>(page.topics());
      StringBuilder libraryCode = new StringBuilder();
      for (ManualConfig.Example example : page.examples()) {
        pageTopics.addAll(example.needs());
        if (example.inLibrary()) {
          libraryCode.append(example.code()).append('\n');
        }
      }
      // The page's library examples together are the library its scripts call.
      LibraryResult library =
          compiler().compileLibrary(libraryCode.toString(), unlocksFor(pageTopics));
      for (int i = 0; i < page.examples().size(); i++) {
        ManualConfig.Example example = page.examples().get(i);
        List<ManualConfig.Topic> topics = new ArrayList<>(page.topics());
        topics.addAll(example.needs());
        List<CompileError> errors =
            example.inLibrary()
                ? library.errors()
                : compiler()
                    .compile(
                        example.code(), library.library(), unlocksFor(topics), turret.memoryLines())
                    .errors();
        if (!errors.isEmpty()) {
          throw new DataException(
              MANUAL, page.id() + " example " + (i + 1) + " does not compile: " + errors);
        }
      }
    }
  }
}
