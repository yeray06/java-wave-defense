package io.github.yeray06.wavedefense.sim.world;

import io.github.yeray06.wavedefense.sim.TestData;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.WaveTable;
import java.util.List;

/** Data variants for battle tests. */
final class BattleTestData {
  private BattleTestData() {}

  /** The shipped data, but wave 1 holds only {@code count} enemies of {@code kind}. */
  static GameData waveOneOf(EnemyKind kind, int count) {
    GameData d = TestData.load();
    WaveTable waves =
        new WaveTable(
            List.of(),
            d.waves().spawnWindowSeconds(),
            List.of(new WaveTable.WaveSpec(1, List.of(new WaveTable.Group(kind, count)))));
    return new GameData(
        d.balance(), d.turret(), d.enemies(), waves, d.shop(), d.map(), d.scriptApi(), d.manual());
  }
}
