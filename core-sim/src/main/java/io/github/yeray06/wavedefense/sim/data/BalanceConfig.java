package io.github.yeray06.wavedefense.sim.data;

import java.util.List;

/**
 * {@code data/balance.json}: the plan's wave formulas and rules. The money of the plan's economy is
 * gone: waves pay stars, once per goal (D-57).
 *
 * @param startingStars stars of a new game
 * @param speeds game speeds the player can pick, from 1 upwards; anything above 1 only on waves
 *     already beaten without leaks (plan, "Farmeo")
 */
public record BalanceConfig(
    List<String> notFromPlan,
    int ticksPerSecond,
    Growth enemyHealth,
    CountRule enemyCount,
    int coreLivesPerWave,
    BossRule boss,
    int lastWave,
    int startingTurrets,
    int startingStars,
    List<Integer> speeds) {

  /** {@code base · growthPerWave^(n−1)}. */
  public record Growth(double base, double growthPerWave) {
    public Growth {
      Check.positive("base", base);
      Check.positive("growthPerWave", growthPerWave);
    }
  }

  /** {@code min(base + perWave · n, max)}. */
  public record CountRule(int base, int perWave, int max) {
    public CountRule {
      Check.atLeastZero("base", base);
      Check.atLeastZero("perWave", perWave);
      Check.positive("max", max);
    }
  }

  /**
   * @param everyWaves a boss closes every wave that is a multiple of this
   */
  public record BossRule(int everyWaves) {
    public BossRule {
      Check.positive("everyWaves", everyWaves);
    }
  }

  public BalanceConfig {
    notFromPlan = List.copyOf(Check.present("notFromPlan", notFromPlan));
    Check.positive("ticksPerSecond", ticksPerSecond);
    Check.present("enemyHealth", enemyHealth);
    Check.present("enemyCount", enemyCount);
    Check.positive("coreLivesPerWave", coreLivesPerWave);
    Check.present("boss", boss);
    Check.positive("lastWave", lastWave);
    Check.positive("startingTurrets", startingTurrets);
    Check.atLeastZero("startingStars", startingStars);
    speeds = List.copyOf(Check.present("speeds", speeds));
    if (speeds.isEmpty() || speeds.get(0) != 1) {
      throw new IllegalArgumentException("speeds must start at 1");
    }
    for (int i = 1; i < speeds.size(); i++) {
      if (speeds.get(i) <= speeds.get(i - 1)) {
        throw new IllegalArgumentException("speeds must grow");
      }
    }
  }
}
