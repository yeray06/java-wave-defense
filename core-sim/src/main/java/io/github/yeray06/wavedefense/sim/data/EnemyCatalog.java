package io.github.yeray06.wavedefense.sim.data;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** {@code data/enemies.json}. */
public record EnemyCatalog(
    List<String> notFromPlan, double armorDamageReduction, Map<EnemyKind, EnemyType> types) {

  /**
   * One enemy type. Abilities a type does not have are written as 0 / false, never left out.
   *
   * @param speed units per second; an integer so {@code velocidad()} returns it exactly (D-22)
   * @param regenDelaySeconds seconds without damage before healing starts (0 = never heals)
   * @param regenFractionPerSecond share of max health healed per second
   * @param groupSize enemies spawned together (the swarm); counts must be a multiple of it
   * @param groupSpacingUnits distance between members of a group
   * @param spawnGapFactor the spawn gap after an enemy (or group) of this type, relative to the
   *     even split of the spawn window; 1 everywhere spreads every wave evenly
   * @param armorToggleSeconds the boss alternates armor this often (0 = never)
   */
  public record EnemyType(
      int speed,
      double healthMultiplier,
      boolean armored,
      double regenDelaySeconds,
      double regenFractionPerSecond,
      int groupSize,
      double groupSpacingUnits,
      double spawnGapFactor,
      double armorToggleSeconds,
      boolean startsArmored) {

    public EnemyType {
      Check.positive("speed", speed);
      Check.positive("healthMultiplier", healthMultiplier);
      Check.atLeastZero("regenDelaySeconds", regenDelaySeconds);
      Check.atLeastZero("regenFractionPerSecond", regenFractionPerSecond);
      Check.positive("groupSize", groupSize);
      Check.atLeastZero("groupSpacingUnits", groupSpacingUnits);
      Check.positive("spawnGapFactor", spawnGapFactor);
      Check.atLeastZero("armorToggleSeconds", armorToggleSeconds);
      if (regenFractionPerSecond > 0 && regenDelaySeconds <= 0) {
        throw new IllegalArgumentException("a regenerating type needs regenDelaySeconds");
      }
    }

    public boolean regenerates() {
      return regenFractionPerSecond > 0;
    }

    public boolean togglesArmor() {
      return armorToggleSeconds > 0;
    }
  }

  public EnemyCatalog {
    notFromPlan = List.copyOf(Check.present("notFromPlan", notFromPlan));
    Check.fraction("armorDamageReduction", armorDamageReduction);
    Check.present("types", types);
    for (EnemyKind kind : EnemyKind.values()) {
      Check.present("types." + kind, types.get(kind));
    }
    types = Map.copyOf(new EnumMap<>(types));
  }

  public EnemyType type(EnemyKind kind) {
    return types.get(kind);
  }
}
