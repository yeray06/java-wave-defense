package io.github.yeray06.wavedefense.script.check;

import io.github.yeray06.wavedefense.script.runtime.Function;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The methods of the library, shared by every turret: what turret scripts are checked against and
 * call at run time. Immutable once built; its executable bodies keep no state, so every turret can
 * run them at once.
 */
public final class Library {
  /** No methods: for players without methods, and for tests. */
  public static final Library EMPTY = new Library(Map.of(), false, 0, 0, List.of());

  /** What a library that could not be compiled at all (an internal error) leaves. */
  public static final Library BROKEN = new Library(Map.of(), true, 0, 0, List.of());

  /**
   * One method.
   *
   * @param paramSlots per parameter, its slot in the method's own region (ints and references are
   *     numbered apart)
   */
  record Method(
      String name, Type returnType, List<Type> params, int[] paramSlots, Function function) {}

  private final Map<String, Method> methods;
  private final boolean hasErrors;
  private final int maxIntSlots;
  private final int maxRefSlots;
  private final List<DebugVar> debugVars;

  Library(
      Map<String, Method> methods,
      boolean hasErrors,
      int maxIntSlots,
      int maxRefSlots,
      List<DebugVar> debugVars) {
    this.methods = Map.copyOf(methods);
    this.debugVars = List.copyOf(debugVars);
    this.hasErrors = hasErrors;
    this.maxIntSlots = maxIntSlots;
    this.maxRefSlots = maxRefSlots;
  }

  /** Whether the library has compile errors: then turrets cannot use it until they are fixed. */
  public boolean hasErrors() {
    return hasErrors;
  }

  /** Names of the methods declared, even with errors; for the editor. */
  public Set<String> methodNames() {
    return methods.keySet();
  }

  /** The most int and boolean slots any one method uses. */
  public int maxIntSlots() {
    return maxIntSlots;
  }

  /** The most reference slots any one method uses. */
  public int maxRefSlots() {
    return maxRefSlots;
  }

  /** Every variable of every method, for the debugger. */
  public List<DebugVar> debugVars() {
    return debugVars;
  }

  Optional<Method> method(String name) {
    return Optional.ofNullable(methods.get(name));
  }
}
