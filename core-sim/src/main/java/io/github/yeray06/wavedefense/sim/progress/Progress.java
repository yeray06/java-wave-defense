package io.github.yeray06.wavedefense.sim.progress;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.economy.Money;
import io.github.yeray06.wavedefense.sim.world.Loadout;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The player's progress: what survives a defeat (plan, "Derrota y reinicio": money, syntax and
 * hardware) plus where they are. Changed only through {@link Shop} and {@link Progression}.
 */
public final class Progress {
  long moneyCents;
  final EnumSet<Feature> features = EnumSet.noneOf(Feature.class);
  final EnumMap<HardwareKind, Integer> levels = new EnumMap<>(HardwareKind.class);
  int currentWave = 1;
  int highestWaveReached = 1;
  boolean autoAdvance = true;
  final BitSet beatenWaves = new BitSet();
  final BitSet perfectWaves = new BitSet();
  final BitSet bossesKilled = new BitSet();
  private final int startingTurrets;

  private Progress(long moneyCents, int startingTurrets) {
    this.moneyCents = moneyCents;
    this.startingTurrets = startingTurrets;
    for (HardwareKind kind : HardwareKind.values()) {
      levels.put(kind, 0);
    }
  }

  /** A new game: plan's starting turret, money from data, auto-advance on (D-02). */
  public static Progress newGame(GameData data) {
    return new Progress(
        Money.cents(data.balance().startingMoney()), data.balance().startingTurrets());
  }

  /**
   * Everything a saved game keeps of the progress, as plain values.
   *
   * @param beatenWaves waves beaten at least once
   * @param perfectWaves waves beaten without leaks (speed-up allowed)
   * @param bossesKilled boss waves whose boss was killed (first-kill pay is spent)
   */
  public record Snapshot(
      long moneyCents,
      Set<Feature> features,
      Map<HardwareKind, Integer> levels,
      int currentWave,
      int highestWaveReached,
      boolean autoAdvance,
      Set<Integer> beatenWaves,
      Set<Integer> perfectWaves,
      Set<Integer> bossesKilled) {
    public Snapshot {
      features = Set.copyOf(features);
      levels = Map.copyOf(levels);
      beatenWaves = Set.copyOf(beatenWaves);
      perfectWaves = Set.copyOf(perfectWaves);
      bossesKilled = Set.copyOf(bossesKilled);
    }
  }

  public Snapshot snapshot() {
    return new Snapshot(
        moneyCents,
        features,
        levels,
        currentWave,
        highestWaveReached,
        autoAdvance,
        waves(beatenWaves),
        waves(perfectWaves),
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
    require(saved.moneyCents() >= 0, "money must not be negative");
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
    progress.moneyCents = saved.moneyCents();
    progress.features.addAll(saved.features());
    for (var level : saved.levels().entrySet()) {
      require(level.getValue() >= 0, "negative level for " + level.getKey());
      var item = data.shop().hardware(level.getKey());
      require(level.getValue() == 0 || item.inMvp(), level.getKey() + " is not sold");
      progress.levels.put(level.getKey(), level.getValue());
    }
    require(
        progress.turretCount() <= data.map().turretSlots().size(),
        "more turrets than slots: " + progress.turretCount());
    progress.currentWave = saved.currentWave();
    progress.highestWaveReached = saved.highestWaveReached();
    progress.autoAdvance = saved.autoAdvance();
    fill(progress.beatenWaves, saved.beatenWaves(), lastWave);
    fill(progress.perfectWaves, saved.perfectWaves(), lastWave);
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
    Progress copy = new Progress(moneyCents, startingTurrets);
    copy.features.addAll(features);
    copy.levels.putAll(levels);
    copy.currentWave = currentWave;
    copy.highestWaveReached = highestWaveReached;
    copy.autoAdvance = autoAdvance;
    copy.beatenWaves.or(beatenWaves);
    copy.perfectWaves.or(perfectWaves);
    copy.bossesKilled.or(bossesKilled);
    return copy;
  }

  /** A copy holding {@code cents} instead of its money, for "what if I spent this" questions. */
  public Progress copyWithMoney(long cents) {
    Progress copy = copy();
    copy.moneyCents = cents;
    return copy;
  }

  public long moneyCents() {
    return moneyCents;
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
        levels.get(HardwareKind.HEAT_SINK));
  }
}
