package io.github.yeray06.wavedefense.sim.world;

import io.github.yeray06.wavedefense.script.diag.FaultType;
import java.util.ArrayList;
import java.util.List;

/**
 * Counts a turret's exceptions by type and line (of its script or of the library), in fixed arrays
 * (no allocation per fault).
 */
final class FaultLog {
  /** Distinct (type, line) pairs kept; a script has at most a few dozen lines that can throw. */
  private static final int CAPACITY = 64;

  private final FaultType[] types = new FaultType[CAPACITY];
  private final int[] lines = new int[CAPACITY];
  private final boolean[] inLibrary = new boolean[CAPACITY];
  private final int[] counts = new int[CAPACITY];
  private int size;
  private int dropped;

  void record(FaultType type, int line, boolean library) {
    for (int i = 0; i < size; i++) {
      if (types[i] == type && lines[i] == line && inLibrary[i] == library) {
        counts[i]++;
        return;
      }
    }
    if (size < CAPACITY) {
      types[size] = type;
      lines[size] = line;
      inLibrary[size] = library;
      counts[size] = 1;
      size++;
    } else {
      dropped++;
    }
  }

  int total() {
    int total = dropped;
    for (int i = 0; i < size; i++) {
      total += counts[i];
    }
    return total;
  }

  List<WaveReport.FaultCount> snapshot() {
    List<WaveReport.FaultCount> list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      list.add(new WaveReport.FaultCount(types[i], lines[i], inLibrary[i], counts[i]));
    }
    return List.copyOf(list);
  }
}
