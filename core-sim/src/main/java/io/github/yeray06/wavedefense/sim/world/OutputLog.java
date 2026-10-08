package io.github.yeray06.wavedefense.sim.world;

import io.github.yeray06.wavedefense.script.api.PrintKind;

/**
 * What the turrets printed with {@code imprimir(x)}, for the output window. A ring of fixed
 * capacity with primitive columns: writing never allocates, and when it is full the oldest lines
 * go. Entries are numbered from 0 for the whole battle; the reader asks for the ones it has not
 * seen yet.
 */
public final class OutputLog {
  /** Lines kept; older ones are dropped. */
  public static final int CAPACITY = 512;

  private final int[] ticks = new int[CAPACITY];
  private final int[] turrets = new int[CAPACITY];
  private final int[] lines = new int[CAPACITY];
  private final boolean[] inLibrary = new boolean[CAPACITY];
  private final PrintKind[] kinds = new PrintKind[CAPACITY];
  private final long[] values = new long[CAPACITY];
  private long written;

  void add(int tick, int turret, int line, boolean library, PrintKind kind, long value) {
    int i = (int) (written % CAPACITY);
    ticks[i] = tick;
    turrets[i] = turret;
    lines[i] = line;
    inLibrary[i] = library;
    kinds[i] = kind;
    values[i] = value;
    written++;
  }

  /** Entries written so far; the next one will have this number. */
  public long written() {
    return written;
  }

  /** The oldest entry still kept. */
  public long oldest() {
    return Math.max(0, written - CAPACITY);
  }

  public int tick(long entry) {
    return ticks[slot(entry)];
  }

  public int turret(long entry) {
    return turrets[slot(entry)];
  }

  public int line(long entry) {
    return lines[slot(entry)];
  }

  public boolean inLibrary(long entry) {
    return inLibrary[slot(entry)];
  }

  public PrintKind kind(long entry) {
    return kinds[slot(entry)];
  }

  public long value(long entry) {
    return values[slot(entry)];
  }

  private int slot(long entry) {
    if (entry < oldest() || entry >= written) {
      throw new IllegalArgumentException("entry " + entry + " is not kept");
    }
    return (int) (entry % CAPACITY);
  }
}
