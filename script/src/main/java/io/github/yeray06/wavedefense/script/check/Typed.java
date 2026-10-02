package io.github.yeray06.wavedefense.script.check;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.runtime.BoolEval;
import io.github.yeray06.wavedefense.script.runtime.Exec;
import io.github.yeray06.wavedefense.script.runtime.IntEval;
import io.github.yeray06.wavedefense.script.runtime.RefEval;

/**
 * A checked expression and its executable form: an {@link IntEval}, {@link BoolEval}, {@link
 * RefEval}, an {@link AmmoType} constant, an {@link Exec} for void calls, or {@code null} for
 * {@link Type#ERROR}.
 */
record Typed(Type type, Object node) {
  static final Typed ERROR = new Typed(Type.ERROR, null);

  IntEval asInt() {
    return (IntEval) node;
  }

  BoolEval asBool() {
    return (BoolEval) node;
  }

  RefEval asRef() {
    return (RefEval) node;
  }
}
