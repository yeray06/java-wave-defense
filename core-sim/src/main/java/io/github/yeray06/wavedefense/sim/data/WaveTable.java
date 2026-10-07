package io.github.yeray06.wavedefense.sim.data;

import java.util.List;

/** {@code data/waves.json}: who comes in each wave, in spawn order. */
public record WaveTable(List<String> notFromPlan, double spawnWindowSeconds, List<WaveSpec> waves) {

  public record Group(EnemyKind type, int count) {
    public Group {
      Check.present("type", type);
      Check.positive("count", count);
    }
  }

  public record WaveSpec(int wave, List<Group> groups) {
    public WaveSpec {
      Check.positive("wave", wave);
      groups = List.copyOf(Check.notEmpty("groups", groups));
    }

    /** Enemies the plan's formula counts: everyone but the boss. */
    public int normalEnemies() {
      return groups.stream().filter(g -> g.type() != EnemyKind.BOSS).mapToInt(Group::count).sum();
    }

    public boolean hasBoss() {
      return groups.stream().anyMatch(g -> g.type() == EnemyKind.BOSS);
    }
  }

  public WaveTable {
    notFromPlan = List.copyOf(Check.present("notFromPlan", notFromPlan));
    Check.positive("spawnWindowSeconds", spawnWindowSeconds);
    waves = List.copyOf(Check.notEmpty("waves", waves));
    for (int i = 0; i < waves.size(); i++) {
      if (waves.get(i).wave() != i + 1) {
        throw new IllegalArgumentException("waves must be listed 1, 2, 3… without gaps");
      }
    }
  }

  public WaveSpec wave(int number) {
    return waves.get(number - 1);
  }

  /** The first wave a kind of enemy comes in, or 0 if it never does. */
  public int firstWave(EnemyKind kind) {
    for (WaveSpec spec : waves) {
      if (spec.groups().stream().anyMatch(g -> g.type() == kind)) {
        return spec.wave();
      }
    }
    return 0;
  }
}
