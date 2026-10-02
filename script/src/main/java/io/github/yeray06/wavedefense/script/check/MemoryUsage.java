package io.github.yeray06.wavedefense.script.check;

import io.github.yeray06.wavedefense.script.ast.Pos;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Counts memory lines: one per statement (declaration, assignment, call, {@code if} header, {@code
 * while} header). Braces, {@code else}, empty statements and comments count zero.
 */
final class MemoryUsage {
  private final int limit;
  private final SortedMap<Integer, Integer> byLine = new TreeMap<>();
  private int used;
  private Pos firstOverLimit;

  MemoryUsage(int limit) {
    this.limit = limit;
  }

  void count(Pos pos) {
    used++;
    byLine.merge(pos.line(), 1, Integer::sum);
    if (used == limit + 1) {
      firstOverLimit = pos;
    }
  }

  int used() {
    return used;
  }

  int limit() {
    return limit;
  }

  /** Where the first statement past the limit is, or {@code null} if within the limit. */
  Pos firstOverLimit() {
    return firstOverLimit;
  }

  SortedMap<Integer, Integer> byLine() {
    return Collections.unmodifiableSortedMap(new TreeMap<>(byLine));
  }
}
