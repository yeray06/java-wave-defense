package io.github.yeray06.wavedefense.script.check;

/**
 * A variable as the debugger shows it: where its value lives in a recording and where it was
 * declared.
 *
 * @param function the method it belongs to, or {@code ""} for a turret's script
 * @param slot its slot among the function's int slots ({@link Kind#INT}, {@link Kind#BOOLEAN}) or
 *     reference slots ({@link Kind#ENEMY}, {@link Kind#LIST})
 * @param line where it is declared: it exists from there on
 */
public record DebugVar(String function, String name, Kind kind, int slot, int line) {

  /** How to read and show the value. */
  public enum Kind {
    INT,
    BOOLEAN,
    ENEMY,
    LIST;

    public boolean isReference() {
      return this == ENEMY || this == LIST;
    }
  }
}
