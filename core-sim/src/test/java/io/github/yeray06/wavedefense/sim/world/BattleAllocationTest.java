package io.github.yeray06.wavedefense.sim.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.yeray06.wavedefense.sim.TestData;
import java.lang.management.ManagementFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The tick is the hot loop: 60 enemies plus bullets must not allocate (CLAUDE.md). */
class BattleAllocationTest {
  private static final String BURST =
      """
      Enemigo o = objetivo();
      if (o == null) {
        o = enemigoMasCercano();
      }
      if (o != null) {
        apuntar(o);
        if (o.blindado()) usarMunicion(PERFORANTE); else usarMunicion(NORMAL);
        while (municion() > 0 && calor() < 80 && objetivo() != null) {
          disparar();
        }
      }
      if (municion() == 0) recargar();
      """;

  @Test
  void tickingAFullWaveAllocatesNothing() {
    java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    assumeTrue(bean instanceof com.sun.management.ThreadMXBean, "needs HotSpot counters");
    com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) bean;
    long thread = Thread.currentThread().threadId();

    // Warm-up battles so the JIT compiles the tick.
    for (int i = 0; i < 5; i++) {
      newBattle().runToEnd();
    }
    // Tough enemies keep the wave full and the turrets busy for the whole measurement.
    Battle battle = newBattle();
    for (int i = 0; i < 600; i++) {
      battle.tick();
    }
    long before = threads.getThreadAllocatedBytes(thread);
    for (int i = 0; i < 1_500; i++) {
      battle.tick();
    }
    long allocated = threads.getThreadAllocatedBytes(thread) - before;
    assertEquals(0, allocated / 1_500, () -> allocated + " bytes in 1,500 ticks");
  }

  private static Battle newBattle() {
    return new Battle(
        TestData.load(),
        new BattleSetup(
            10,
            List.of(TestData.script(BURST), TestData.script(BURST)),
            new Loadout(2, 2, 2),
            50,
            7L));
  }
}
