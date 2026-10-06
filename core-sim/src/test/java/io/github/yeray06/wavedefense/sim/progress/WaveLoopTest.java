package io.github.yeray06.wavedefense.sim.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.sim.TestData;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.world.Battle;
import org.junit.jupiter.api.Test;

/** D-01: the wave loop the game screen drives. */
class WaveLoopTest {
  private static final int LIMIT = 200_000;

  private static GameData data() {
    return TestData.load();
  }

  /** Ticks until a wave finishes; the player reloads by hand whenever a turret is empty. */
  private static void finishWave(WaveLoop loop) {
    int finished = loop.wavesFinished();
    for (int i = 0; i < LIMIT && loop.wavesFinished() == finished; i++) {
      Battle battle = loop.battle();
      for (int t = 0; t < battle.turretCount(); t++) {
        if (battle.turret(t).ammo() == 0) {
          loop.manualReload(t);
        }
      }
      loop.tick();
    }
    assertEquals(finished + 1, loop.wavesFinished(), "the wave did not end");
  }

  @Test
  void aNewGameWaitsForDeployOnWaveOneWithTheFieldBuilt() {
    WaveLoop loop = new WaveLoop(data(), Progress.newGame(data()));
    assertEquals(WaveLoop.State.AWAITING_DEPLOY, loop.state());
    assertEquals(1, loop.battle().wave());
    assertEquals(1, loop.battle().turretCount());
    assertTrue(loop.battle().turret(0).hasScript());
    assertNull(loop.lastOutcome());
    int tick = loop.battle().tickCount();
    loop.tick();
    assertEquals(tick, loop.battle().tickCount(), "nothing moves before deploying");
    assertFalse(loop.manualReload(0), "no reload before the wave starts");
  }

  @Test
  void anUnbeatenWaveWaitsAgainAfterTheOneBefore() {
    WaveLoop loop = new WaveLoop(data(), Progress.newGame(data()));
    loop.deploy();
    assertEquals(WaveLoop.State.RUNNING, loop.state());
    finishWave(loop);
    assertTrue(loop.lastOutcome().won(), "disparar() and hand reloads beat wave 1");
    assertEquals(1, loop.lastReport().wave());
    assertEquals(2, loop.battle().wave());
    assertEquals(WaveLoop.State.AWAITING_DEPLOY, loop.state());
    assertTrue(loop.progress().moneyCents() > 0);
  }

  @Test
  void repetitionsChainWithoutPauseWhenAutoAdvanceIsOff() {
    Progress progress = Progress.newGame(data());
    WaveLoop loop = new WaveLoop(data(), progress);
    loop.deploy();
    finishWave(loop);
    progress.setAutoAdvance(false);
    loop.deploy();
    finishWave(loop);
    // Wave 2 beaten with auto-advance off: it repeats at once (D-01).
    assertEquals(2, loop.battle().wave());
    assertEquals(WaveLoop.State.RUNNING, loop.state());
    assertTrue(loop.speedUpAllowed() == progress.speedUpAllowed(2));
  }

  @Test
  void aDefeatGoesBackToABeatenWaveAndChains() {
    Progress progress = Progress.newGame(data());
    progress.beatenWaves.set(1, 10);
    progress.currentWave = 9;
    progress.highestWaveReached = 9;
    WaveLoop loop = new WaveLoop(data(), progress);
    assertEquals(WaveLoop.State.RUNNING, loop.state(), "wave 9 was beaten before");
    finishWave(loop);
    assertFalse(loop.lastOutcome().won(), "disparar() cannot hold wave 9");
    assertEquals(8, loop.battle().wave());
    assertEquals(WaveLoop.State.RUNNING, loop.state());
    assertFalse(progress.autoAdvance());
  }

  @Test
  void everyBoughtTurretRunsTheInitialScript() {
    Progress progress = Progress.newGame(data());
    progress.levels.put(io.github.yeray06.wavedefense.sim.data.HardwareKind.EXTRA_TURRET, 1);
    WaveLoop loop = new WaveLoop(data(), progress);
    assertEquals(2, loop.battle().turretCount());
    assertTrue(loop.battle().turret(1).hasScript());
  }

  @Test
  void theSameInputsReplayTheSameGame() {
    WaveLoop first = new WaveLoop(data(), Progress.newGame(data()));
    WaveLoop second = new WaveLoop(data(), Progress.newGame(data()));
    for (int wave = 0; wave < 3; wave++) {
      first.deploy();
      second.deploy();
      finishWave(first);
      finishWave(second);
      assertEquals(first.lastReport(), second.lastReport());
    }
    assertSame(WaveLoop.State.AWAITING_DEPLOY, first.state());
  }
}
