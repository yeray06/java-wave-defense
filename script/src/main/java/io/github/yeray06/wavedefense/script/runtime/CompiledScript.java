package io.github.yeray06.wavedefense.script.runtime;

import io.github.yeray06.wavedefense.script.api.ScriptLimits;
import io.github.yeray06.wavedefense.script.api.TurretApi;
import io.github.yeray06.wavedefense.script.diag.CycleOutcome;
import io.github.yeray06.wavedefense.script.diag.FaultType;

/**
 * A script ready to run once per turret cycle. Not thread-safe: one instance per turret.
 *
 * <p>{@link #run(TurretApi)} never throws and never allocates: game exceptions, reloads and even
 * bugs in the interpreter or the turret come back as a {@link CycleOutcome}.
 */
public final class CompiledScript {
  private final Exec root;
  private final Frame frame;
  private FaultType faultType;
  private int faultLine;
  private boolean faultInLibrary;
  private Throwable internalError;

  public CompiledScript(Exec root, int intSlots, int refSlots, ScriptLimits limits) {
    this.root = root;
    this.frame = new Frame(intSlots, refSlots, limits);
  }

  public CycleOutcome run(TurretApi turret) {
    faultType = null;
    faultLine = 0;
    faultInLibrary = false;
    internalError = null;
    frame.reset(turret);
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
