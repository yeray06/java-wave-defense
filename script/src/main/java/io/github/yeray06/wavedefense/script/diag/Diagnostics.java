package io.github.yeray06.wavedefense.script.diag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Collects compile errors from every stage, capped so a garbage script stays cheap. */
public final class Diagnostics {
  /** More errors than this help nobody; the editor shows the first ones. */
  static final int MAX_ERRORS = 50;

  private final Set<CompileError> errors = new LinkedHashSet<>();

  public void report(ErrorCode code, int line, int column, Object... args) {
    if (errors.size() < MAX_ERRORS) {
      errors.add(new CompileError(code, line, column, List.of(args)));
    }
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  /** Errors sorted by position, without duplicates. */
  public List<CompileError> sorted() {
    List<CompileError> list = new ArrayList<>(errors);
    list.sort(Comparator.comparingInt(CompileError::line).thenComparingInt(CompileError::column));
    return List.copyOf(list);
  }
}
