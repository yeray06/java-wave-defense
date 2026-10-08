package io.github.yeray06.wavedefense.sim.progress;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.world.Loadout;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The player's progress: what survives a defeat (stars, trophies, syntax and hardware) plus where
 * they are and which of each wave's goals they have met (D-57). Changed only through {@link Shop}
 * and {@link Progression}.
 */
public final class Progress {
  /** Stars to spend: every goal met adds one, every purchase takes its price (D-57). */
  int stars;

  /** Boss trophies to spend: one per boss killed for the first time (D-57). */
  int trophies;

  final EnumSet<Feature> features = EnumSet.noneOf(Feature.class);
  final EnumMap<HardwareKind, Integer> levels = new EnumMap<>(HardwareKind.class);
  int currentWave = 1;
  int highestWaveReached = 1;
  boolean autoAdvance = true;
  final BitSet beatenWaves = new BitSet();
  final BitSet perfectWaves = new BitSet();
  final BitSet challengeWaves = new BitSet();
  final BitSet bossesKilled = new BitSet();
  private final int startingTurrets;

  private Progress(int stars, int startingTurrets) {
    this.stars = stars;
    this.startingTurrets = startingTurrets;
    for (HardwareKind kind : HardwareKind.values()) {
      levels.put(kind, 0);
    }
  }

  /** A new game: plan's starting turret, stars from data, auto-advance on (D-02). */
  public static Progress newGame(GameData data) {
    return new Progress(data.balance().startingStars(), data.balance().startingTurrets());
  }

  /**
   * An expert game (D-64): every syntax piece on sale is owned from the start, for players who
   * already program. Stars, hardware and waves are as in a normal game.
   */
  public static Progress newExpertGame(GameData data) {
    Progress progress = newGame(data);
    for (var item : data.shop().syntax()) {
      if (item.inMvp()) {
        progress.features.add(item.feature());
      }
    }
    return progress;
  }

  /**
   * Everything a saved game keeps of the progress, as plain values.
   *
   * @param stars stars not spent yet
   * @param trophies boss trophies not spent yet
   * @param beatenWaves waves beaten at least once: their first star
   * @param perfectWaves waves beaten without leaks: their second star (and speed-up allowed)
   * @param challengeWaves waves whose code challenge was met: their third star
   * @param bossesKilled boss waves whose boss was killed: their trophy
   */
  public record Snapshot(
      int stars,
      int trophies,
      Set<Feature> features,
      Map<HardwareKind, Integer> levels,
      int currentWave,
      int highestWaveReached,
      boolean autoAdvance,
      Set<Integer> beatenWaves,
      Set<Integer> perfectWaves,
      Set<Integer> challengeWaves,
      Set<Integer> bossesKilled) {
    public Snapshot {
      features = Set.copyOf(features);
      levels = Map.copyOf(levels);
      beatenWaves = Set.copyOf(beatenWaves);
      perfectWaves = Set.copyOf(perfectWaves);
      challengeWaves = Set.copyOf(challengeWaves);
      bossesKilled = Set.copyOf(bossesKilled);
    }
  }

  public Snapshot snapshot() {
    return new Snapshot(
        stars,
        trophies,
        features,
        levels,
        currentWave,
        highestWaveReached,
        autoAdvance,
        waves(beatenWaves),
        waves(perfectWaves),
        waves(challengeWaves),
        waves(bossesKilled));
  }

  /**
   * Progress from a saved game, checked against the data so a hand-edited or outdated file cannot
   * break the rules.
   *
   * @throws IllegalArgumentException naming what does not fit
   */
  public static Progress restore(GameData data, Snapshot saved) {
    int lastWave = data.balance().lastWave();
    require(saved.stars() >= 0, "stars must not be negative");
    require(saved.trophies() >= 0, "trophies must not be negative");
    require(
        saved.highestWaveReached() >= 1 && saved.highestWaveReached() <= lastWave,
        "highest wave out of range: " + saved.highestWaveReached());
    require(
        saved.currentWave() >= 1 && saved.currentWave() <= saved.highestWaveReached(),
        "current wave out of range: " + saved.currentWave());
    for (Feature feature : saved.features()) {
      var item = data.shop().syntax(feature);
      require(item.inMvp(), feature + " is not sold");
      require(
          item.requires() == null || saved.features().contains(item.requires()),
          feature + " without " + item.requires());
    }
    Progress progress = newGame(data);
    progress.stars = saved.stars();
    progress.trophies = saved.trophies();
    progress.features.addAll(saved.features());
    for (var level : saved.levels().entrySet()) {
      require(level.getValue() >= 0, "negative level for " + level.getKey());
      var item = data.shop().hardware(level.getKey());
      require(level.getValue() == 0 || item.inMvp(), level.getKey() + " is not sold");
      // A level above the highest one (an old save, or data that lowered it) keeps the highest.
      progress.levels.put(level.getKey(), Math.min(level.getValue(), item.maxLevel()));
    }
    require(
        progress.turretCount() <= data.map().turretSlots().size(),
        "more turrets than slots: " + progress.turretCount());
    progress.currentWave = saved.currentWave();
    progress.highestWaveReached = saved.highestWaveReached();
    progress.autoAdvance = saved.autoAdvance();
    fill(progress.beatenWaves, saved.beatenWaves(), lastWave);
    fill(progress.perfectWaves, saved.perfectWaves(), lastWave);
    fill(progress.challengeWaves, saved.challengeWaves(), lastWave);
    fill(progress.bossesKilled, saved.bossesKilled(), lastWave);
    return progress;
  }

  private static Set<Integer> waves(BitSet bits) {
    return bits.stream().boxed().collect(Collectors.toSet());
  }

  private static void fill(BitSet bits, Set<Integer> waves, int lastWave) {
    for (int wave : waves) {
      require(wave >= 1 && wave <= lastWave, "wave out of range: " + wave);
      bits.set(wave);
    }
  }

  private static void require(boolean condition, String problem) {
    if (!condition) {
      throw new IllegalArgumentException(problem);
    }
  }

  /** An independent copy, for what-if simulations. */
  public Progress copy() {
    Progress copy = new Progress(stars, startingTurrets);
    copy.trophies = trophies;
    copy.features.addAll(features);
    copy.levels.putAll(levels);
    copy.currentWave = currentWave;
    copy.highestWaveReached = highestWaveReached;
    copy.autoAdvance = autoAdvance;
    copy.beatenWaves.or(beatenWaves);
    copy.perfectWaves.or(perfectWaves);
    copy.challengeWaves.or(challengeWaves);
    copy.bossesKilled.or(bossesKilled);
    return copy;
  }

  /** A copy holding {@code stars} instead of its own, for "what if I spent this" questions. */
  public Progress copyWithStars(int stars) {
    Progress copy = copy();
    copy.stars = stars;
    return copy;
  }

  /** Stars not spent yet. */
  public int stars() {
    return stars;
  }

  /** Boss trophies not spent yet. */
  public int trophies() {
    return trophies;
  }

  /** Whether a wave's goal is met: each one paid its star once (D-57). */
  public boolean met(int wave, Goal goal) {
    return switch (goal) {
      case CLEAR -> beatenWaves.get(wave);
      case PERFECT -> perfectWaves.get(wave);
      case CHALLENGE -> challengeWaves.get(wave);
    };
  }

  /** Stars earned in the whole game, spent or not: one per goal met. */
  public int starsEarned() {
    return beatenWaves.cardinality() + perfectWaves.cardinality() + challengeWaves.cardinality();
  }

  public Set<Feature> features() {
    return Set.copyOf(features);
  }

  public boolean owns(Feature feature) {
    return features.contains(feature);
  }

  public int level(HardwareKind kind) {
    return levels.get(kind);
  }

  public Map<HardwareKind, Integer> levels() {
    return Map.copyOf(levels);
  }

  public int currentWave() {
    return currentWave;
  }

  /**
   * Goes to a wave already reached, as the wave map does (D-49).
   *
   * @return false for a wave not reached yet
   */
  public boolean goTo(int wave) {
    if (wave < 1 || wave > highestWaveReached) {
      return false;
    }
    currentWave = wave;
    return true;
  }

  public int highestWaveReached() {
    return highestWaveReached;
  }

  public boolean autoAdvance() {
    return autoAdvance;
  }

  /** The player's switch. */
  public void setAutoAdvance(boolean on) {
    autoAdvance = on;
  }

  public boolean beaten(int wave) {
    return beatenWaves.get(wave);
  }

  /** Speed ×3 is allowed on waves already beaten without leaks (plan, "Farmeo"). */
  public boolean speedUpAllowed(int wave) {
    return perfectWaves.get(wave);
  }

  public boolean bossKilled(int wave) {
    return bossesKilled.get(wave);
  }

  public int turretCount() {
    return startingTurrets + levels.get(HardwareKind.EXTRA_TURRET);
  }

  public Unlocks unlocks() {
    return new Unlocks(features, highestWaveReached);
  }

  public Loadout loadout() {
    return new Loadout(
        levels.get(HardwareKind.DAMAGE),
        levels.get(HardwareKind.MAGAZINE),
        levels.get(HardwareKind.HEAT_SINK),
        levels.get(HardwareKind.PROCESSOR));
  }

  /** Memory lines each turret's code may use: the turret's plus the memory upgrades. */
  public int memoryLines(GameData data) {
    return (int)
        Math.round(
            data.turret().memoryLines()
                + data.shop().hardware(HardwareKind.MEMORY).effectPerLevel()
                    * levels.get(HardwareKind.MEMORY));
  }
}
