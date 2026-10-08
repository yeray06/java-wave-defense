package io.github.yeray06.wavedefense.script.runtime;

import io.github.yeray06.wavedefense.script.api.ScriptLimits;
import io.github.yeray06.wavedefense.script.api.TurretApi;
import io.github.yeray06.wavedefense.script.diag.CycleOutcome;
import io.github.yeray06.wavedefense.script.diag.FaultType;
import java.util.BitSet;

/**
 * A script ready to run once per turret cycle. Not thread-safe: one instance per turret.
 *
 * <p>{@link #run(TurretApi)} never throws and never allocates: game exceptions, reloads and even
 * bugs in the interpreter or the turret come back as a {@link CycleOutcome}.
 */
public final class CompiledScript {
  /** Steps the debugger keeps of one run: far more than a readable cycle has. */
  public static final int TRACE_STEPS = 4096;

  private final Exec root;
  private final Function script;
  private final Frame frame;
  private final int traceIntWidth;
  private final int traceRefWidth;
  private Tracer tracer;
  private boolean stopped;
  private FaultType faultType;
  private int faultLine;
  private boolean faultInLibrary;
  private Throwable internalError;

  /**
   * @param intSlots, refSlots the whole stack: the script and the deepest chain of library calls
   * @param traceIntWidth, traceRefWidth the most slots one function uses, for the debugger
   */
  public CompiledScript(
      Exec root,
      Function script,
      int intSlots,
      int refSlots,
      int traceIntWidth,
      int traceRefWidth,
      ScriptLimits limits) {
    this.root = root;
    this.script = script;
    this.frame = new Frame(intSlots, refSlots, limits);
    this.traceIntWidth = traceIntWidth;
    this.traceRefWidth = traceRefWidth;
  }

  /**
   * The player's breakpoints: lines of this script and of the library. While there is any, each run
   * is recorded; one that reaches a breakpoint is {@link #stopped()}. The recording space is
   * created the first time, outside the game's hot loop.
   */
  public void setBreakpoints(BitSet scriptLines, BitSet libraryLines) {
    tracer().setBreakpoints(scriptLines, libraryLines);
  }

  /** «Paso a paso»: the next run is recorded and stops at its first step. */
  public void breakOnNextRun() {
    tracer().breakOnNextRun();
  }

  /** Whether the last run reached a breakpoint: its recording is in {@link #trace()}. */
  public boolean stopped() {
    return stopped;
  }

  /** The recording of the last run, or {@code null} if debugging was never used. */
  public Tracer trace() {
    return tracer;
  }

  /** The script's own function, to tell its steps from the library's in a recording. */
  public Function function() {
    return script;
  }

  private Tracer tracer() {
    if (tracer == null) {
      tracer = new Tracer(TRACE_STEPS, traceIntWidth, traceRefWidth);
    }
    return tracer;
  }

  public CycleOutcome run(TurretApi turret) {
    faultType = null;
    faultLine = 0;
    faultInLibrary = false;
    internalError = null;
    stopped = false;
    frame.reset(turret, script);
    if (tracer != null && tracer.active()) {
      tracer.start();
      frame.tracer = tracer;
    }
    try {
      root.exec(frame);
      return CycleOutcome.COMPLETED;
    } catch (Return returned) {
      return CycleOutcome.COMPLETED;
    } catch (Signal signal) {
      if (signal.fault == null) {
        return CycleOutcome.RELOADING;
      }
      faultType = signal.fault;
      faultLine = signal.line;
      faultInLibrary = signal.inLibrary;
      return CycleOutcome.FAULT;
    } catch (RuntimeException | StackOverflowError e) {
      faultType = FaultType.INTERNAL_ERROR;
      faultLine = frame.currentLine;
      internalError = e;
      return CycleOutcome.FAULT;
    } finally {
      frame.api = null;
      if (frame.tracer != null) {
        stopped = tracer.firstBreak() >= 0;
        frame.tracer = null;
      }
    }
  }

  /** Exception raised by the last run, or {@code null} if it did not fault. */
  public FaultType faultType() {
    return faultType;
  }

  /** Line of the last fault, or 0. */
  public int faultLine() {
    return faultLine;
  }

  /** Whether {@link #faultLine()} is a line of the library rather than of the turret's script. */
  public boolean faultInLibrary() {
    return faultInLibrary;
  }

  /** Cause of an {@link FaultType#INTERNAL_ERROR}, for the log; otherwise {@code null}. */
  public Throwable internalError() {
    return internalError;
  }
}
