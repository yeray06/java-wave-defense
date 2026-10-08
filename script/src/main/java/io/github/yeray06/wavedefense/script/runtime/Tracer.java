package io.github.yeray06.wavedefense.script.runtime;

import java.util.BitSet;

/**
 * The debugger's recording of one script run: for every step (each statement and each loop turn),
 * the line, whether it was in the library, which function was running and that function's slot
 * values. Arrays are created once, when the player first sets a breakpoint; recording never
 * allocates. The world does not move while a script runs, so stepping through the recording after
 * the cycle shows exactly what happened.
 */
public final class Tracer {
  private final int capacity;
  private final int intWidth;
  private final int refWidth;
  private final int[] lines;
  private final boolean[] inLibrary;
  private final Function[] functions;
  private final int[] ints;
  private final long[] refs;
  private final BitSet scriptBreaks = new BitSet();
  private final BitSet libraryBreaks = new BitSet();
  private boolean breakNextRun;
  private boolean armedThisRun;
  private int steps;
  private boolean truncated;
  private int firstBreak = -1;

  Tracer(int capacity, int intWidth, int refWidth) {
    this.capacity = capacity;
    this.intWidth = intWidth;
    this.refWidth = refWidth;
    this.lines = new int[capacity];
    this.inLibrary = new boolean[capacity];
    this.functions = new Function[capacity];
    this.ints = new int[capacity * Math.max(1, intWidth)];
    this.refs = new long[capacity * Math.max(1, refWidth)];
  }

  void setBreakpoints(BitSet scriptLines, BitSet libraryLines) {
    scriptBreaks.clear();
    scriptBreaks.or(scriptLines);
    libraryBreaks.clear();
    libraryBreaks.or(libraryLines);
  }

  void breakOnNextRun() {
    breakNextRun = true;
  }

  /** Whether this tracer has anything to stop at: otherwise runs skip recording. */
  boolean active() {
    return breakNextRun || !scriptBreaks.isEmpty() || !libraryBreaks.isEmpty();
  }

  void start() {
    steps = 0;
    truncated = false;
    firstBreak = -1;
    armedThisRun = breakNextRun;
    breakNextRun = false;
  }

  void record(Frame frame, int line) {
    boolean library = frame.depth > 0;
    if (firstBreak < 0
        && (armedThisRun || (library ? libraryBreaks.get(line) : scriptBreaks.get(line)))) {
      firstBreak = steps;
    }
    if (steps == capacity) {
      truncated = true;
      return;
    }
    int step = steps++;
    lines[step] = line;
    inLibrary[step] = library;
    functions[step] = frame.function;
    int ints0 = step * Math.max(1, intWidth);
    int intCount = Math.min(intWidth, frame.ints.length - frame.intBase);
    System.arraycopy(frame.ints, frame.intBase, ints, ints0, Math.max(0, intCount));
    int refs0 = step * Math.max(1, refWidth);
    int refCount = Math.min(refWidth, frame.refs.length - frame.refBase);
    System.arraycopy(frame.refs, frame.refBase, refs, refs0, Math.max(0, refCount));
  }

  /** Steps recorded in the last run. */
  public int steps() {
    return steps;
  }

  /** Whether the run had more steps than could be kept; the first ones are kept. */
  public boolean truncated() {
    return truncated;
  }

  /** The first step that hit a breakpoint (or the first step, after «pause next cycle»), or -1. */
  public int firstBreak() {
    return firstBreak;
  }

  public int line(int step) {
    return lines[step];
  }

  public boolean inLibrary(int step) {
    return inLibrary[step];
  }

  /** The function running at {@code step}: the script's or one of the library's. */
  public Function function(int step) {
    return functions[step];
  }

  /** The value in int slot {@code slot} of the running function at {@code step}. */
  public int intValue(int step, int slot) {
    return ints[step * Math.max(1, intWidth) + slot];
  }

  public long refValue(int step, int slot) {
    return refs[step * Math.max(1, refWidth) + slot];
  }
}
