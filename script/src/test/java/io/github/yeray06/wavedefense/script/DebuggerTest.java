package io.github.yeray06.wavedefense.script;

import static io.github.yeray06.wavedefense.script.Scripts.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.check.DebugVar;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import io.github.yeray06.wavedefense.script.runtime.Tracer;
import java.util.BitSet;
import org.junit.jupiter.api.Test;

/** Breakpoints and step-by-step: a run is recorded, then read back step by step. */
class DebuggerTest {
  private static final String COUNTING =
      """
      int n = 0;
      while (n < 3) {
        n++;
      }
      disparar();
      """;

  private static BitSet lines(int... lines) {
    BitSet set = new BitSet();
    for (int line : lines) {
      set.set(line);
    }
    return set;
  }

  private static DebugVar var(CompileResult result, String name) {
    return result.debugVars().stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
  }

  @Test
  void withoutBreakpointsNothingIsRecorded() {
    CompiledScript script = ok(COUNTING);
    script.run(new FakeTurret());
    assertFalse(script.stopped());
    assertNull(script.trace());
  }

  @Test
  void aBreakpointStopsTheRunAndEveryStepCanBeRead() {
    CompileResult result = Scripts.compile(COUNTING);
    CompiledScript script = result.script().orElseThrow();
    script.setBreakpoints(lines(3), new BitSet());
    FakeTurret turret = new FakeTurret();
    script.run(turret);
    assertEquals(1, turret.shotsFired, "recording does not change what the script does");
    assertTrue(script.stopped());
    Tracer trace = script.trace();
    int first = trace.firstBreak();
    assertEquals(3, trace.line(first));
    DebugVar n = var(result, "n");
    assertEquals(DebugVar.Kind.INT, n.kind());
    assertEquals(1, n.line());
    // Steps record the values before the statement runs: n is 0 the first time line 3 comes up.
    assertEquals(0, trace.intValue(first, n.slot()));
    int last = trace.steps() - 1;
    assertEquals(5, trace.line(last));
    assertEquals(3, trace.intValue(last, n.slot()));
    assertSame(script.function(), trace.function(first));
  }

  @Test
  void aRunThatMissesTheBreakpointDoesNotStop() {
    CompiledScript script = ok("if (hayEnemigos()) {\n  disparar();\n}");
    script.setBreakpoints(lines(2), new BitSet());
    script.run(new FakeTurret());
    assertFalse(script.stopped());
  }

  @Test
  void pauseOnTheNextRunStopsAtItsFirstStepOnce() {
    CompiledScript script = ok(COUNTING);
    script.breakOnNextRun();
    script.run(new FakeTurret());
    assertTrue(script.stopped());
    assertEquals(0, script.trace().firstBreak());
    assertEquals(1, script.trace().line(0));
    script.run(new FakeTurret());
    assertFalse(script.stopped(), "only the next run");
  }

  @Test
  void aBreakpointInTheLibraryStopsInsideTheMethod() {
    var library = Scripts.okLibrary("int doble(int x) {\n  int y = x * 2;\n  return y;\n}");
    CompileResult result = Scripts.compile("int a = doble(4);\ndisparar();", library);
    CompiledScript script = result.script().orElseThrow();
    script.setBreakpoints(new BitSet(), lines(3));
    script.run(new FakeTurret());
    assertTrue(script.stopped());
    Tracer trace = script.trace();
    int step = trace.firstBreak();
    assertTrue(trace.inLibrary(step));
    assertEquals("doble", trace.function(step).name());
    DebugVar y =
        library.debugVars().stream().filter(v -> v.name().equals("y")).findFirst().orElseThrow();
    assertEquals("doble", y.function());
    assertEquals(8, trace.intValue(step, y.slot()));
    DebugVar x =
        library.debugVars().stream().filter(v -> v.name().equals("x")).findFirst().orElseThrow();
    assertEquals(4, trace.intValue(step, x.slot()));
  }
}
