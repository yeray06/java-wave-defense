package io.github.yeray06.wavedefense.script.runtime;

import io.github.yeray06.wavedefense.script.api.EnemyHandle;
import io.github.yeray06.wavedefense.script.api.ScriptLimits;
import io.github.yeray06.wavedefense.script.api.TurretApi;
import io.github.yeray06.wavedefense.script.diag.FaultType;
import java.util.Arrays;

/**
 * State of one script execution: local variables in primitive slots, the step budget and the
 * turret. Allocated once per compiled script and reset at the start of every cycle.
 *
 * <p>The slots are a stack: the turret's script uses the bottom, and each running library call gets
 * its own region above its caller's, starting at {@link #intBase} and {@link #refBase}. The arrays
 * are sized for the deepest call allowed, so calling never allocates.
 */
public final class Frame {
  /** {@code int} and {@code boolean} (0/1) variables. */
  final int[] ints;

  /** Enemy and list variables as handles; {@link EnemyHandle#NONE} is {@code null}. */
  final long[] refs;

  private final ScriptLimits limits;
  private final Signal signal = new Signal();

  /** Thrown by {@code return}; caught by the call it returns from. */
  final Return returnSignal = new Return();

  TurretApi api;
  private int steps;

  /** Where the running function's slots start. */
  int intBase;

  int refBase;

  /** Library calls running now; 0 while in the turret's own script. */
  int depth;

  /** What the last {@code return value;} returned: ints and booleans (0/1), or a reference. */
  int returnedInt;

  long returnedRef;

  /** Line of the innermost running {@code while}, or 0. */
  int loopLine;

  /** Line of the statement being run, for internal errors. */
  int currentLine;

  Frame(int intSlots, int refSlots, ScriptLimits limits) {
    this.ints = new int[intSlots];
    this.refs = new long[refSlots];
    this.limits = limits;
  }

  void reset(TurretApi turret) {
    Arrays.fill(ints, 0);
    Arrays.fill(refs, EnemyHandle.NONE);
    api = turret;
    steps = 0;
    intBase = 0;
    refBase = 0;
    depth = 0;
    loopLine = 0;
    currentLine = 0;
  }

  int maxLoopIterations() {
    return limits.maxLoopIterations();
  }

  int maxCallDepth() {
    return limits.maxCallDepth();
  }

  /** Charges one step; past the cycle budget the running loop is reported as infinite. */
  void step(int line) {
    currentLine = line;
    if (++steps > limits.maxStepsPerCycle()) {
      throw fault(FaultType.INFINITE_LOOP, loopLine != 0 ? loopLine : line);
    }
  }

  /** A game exception at {@code line}: of the library if a library call is running. */
  Signal fault(FaultType type, int line) {
    signal.fault = type;
    signal.line = line;
    signal.inLibrary = depth > 0;
    return signal;
  }

  Signal stop(int line) {
    signal.fault = null;
    signal.line = line;
    signal.inLibrary = depth > 0;
    return signal;
  }

  /**
   * The handle if it refers to a living enemy; otherwise a NullPointerException at {@code line}.
   */
  long enemy(long handle, int line) {
    if (handle == EnemyHandle.NONE || !api.exists(handle)) {
      throw fault(FaultType.NULL_POINTER, line);
    }
    return handle;
  }
}
