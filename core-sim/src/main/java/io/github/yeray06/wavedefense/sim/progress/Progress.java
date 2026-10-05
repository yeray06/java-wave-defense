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
