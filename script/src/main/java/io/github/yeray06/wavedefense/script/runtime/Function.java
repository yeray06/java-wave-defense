package io.github.yeray06.wavedefense.script.runtime;

/**
 * What a call needs to know about a function: how many slots it uses and, for a library method, its
 * body. The checker creates one per function before checking any body, so calls (even a method
 * calling itself) can point to it, and fills it in once the body is checked. Never changes
 * afterwards.
 */
public final class Function {
  private final String name;
  Exec body;
  int intSlots;
  int refSlots;

  /** {@code name} is the method's, or empty for a turret's script. */
  public Function(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  public void define(Exec body, int intSlots, int refSlots) {
    this.body = body;
    this.intSlots = intSlots;
    this.refSlots = refSlots;
  }

  public int intSlots() {
    return intSlots;
  }

  public int refSlots() {
    return refSlots;
  }
}
