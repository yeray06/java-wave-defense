package io.github.yeray06.wavedefense.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.yeray06.wavedefense.script.diag.CycleOutcome;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;

/** Running a script is in the game's hot loop: it must not allocate (CLAUDE.md, pooling rule). */
class AllocationTest {
  private static final String BUSY_SCRIPT =
      """
      Enemigo o = objetivo();
      if (o == null || o.vida() <= 0) {
        o = enemigoMasCercano();
        if (o != null) apuntar(o);
      }
      int n = 0;
      while (n < 50 && calor() < 100 && municion() > 0) {
        n += 1 + n % 3 / 2;
        if (o != null && o.blindado() && o.distanciaAlNucleo() * 2 > 100) usarMunicion(PERFORANTE);
      }
      for (Enemigo e : enemigos()) {
        if (e.vida() < 50) n++;
      }
      for (int i = 0; i < enemigos().size() && i < 3; i++) {
        n += enemigos().get(i).velocidad();
      }
      disparar();
      """;

  @Test
  void runningACycleAllocatesNothing() {
    java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    assumeTrue(
        bean instanceof com.sun.management.ThreadMXBean, "needs HotSpot allocation counters");
    com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) bean;
    long thread = Thread.currentThread().threadId();

    CompiledScript script = Scripts.ok(BUSY_SCRIPT);
    FakeTurret turret = new FakeTurret();
    turret.nearest = turret.addEnemy(1, 40, 80, true, 900);
    turret.addEnemy(2, 60, 120, false, 700);

    // Warm up so the JIT has compiled the interpreter before measuring.
    for (int i = 0; i < 50_000; i++) {
      turret.ammo = 12;
      turret.heat = 0;
      script.run(turret);
    }
    long before = threads.getThreadAllocatedBytes(thread);
    for (int i = 0; i < 100_000; i++) {
      turret.ammo = 12;
      turret.heat = 0;
      script.run(turret);
    }
    long allocated = threads.getThreadAllocatedBytes(thread) - before;

    assertEquals(CycleOutcome.COMPLETED, script.run(turret));
    // A few bytes of noise from the measurement itself are tolerated; 1 byte per cycle is not.
    assertEquals(0, allocated / 100_000, () -> allocated + " bytes in 100,000 cycles");
  }
}
