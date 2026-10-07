package io.github.yeray06.wavedefense.game.editor;

import com.badlogic.gdx.graphics.Color;
import io.github.yeray06.wavedefense.game.editor.Highlighter.Style;

/** The colour of each kind of code, shared by the editor and the manual's examples. */
final class CodeColors {
  static final Color BACKGROUND = new Color(0.09f, 0.10f, 0.12f, 1f);

  private static final Color[] STYLE = new Color[Style.values().length];

  static {
    STYLE[Style.PLAIN.ordinal()] = new Color(0.88f, 0.90f, 0.93f, 1f);
    STYLE[Style.KEYWORD.ordinal()] = new Color(0.80f, 0.55f, 0.95f, 1f);
    STYLE[Style.TYPE.ordinal()] = new Color(0.45f, 0.80f, 0.95f, 1f);
    STYLE[Style.CALL.ordinal()] = new Color(0.95f, 0.85f, 0.50f, 1f);
    STYLE[Style.NUMBER.ordinal()] = new Color(0.70f, 0.90f, 0.60f, 1f);
    STYLE[Style.CONSTANT.ordinal()] = new Color(0.95f, 0.60f, 0.45f, 1f);
    STYLE[Style.COMMENT.ordinal()] = new Color(0.50f, 0.55f, 0.60f, 1f);
  }

  private CodeColors() {}

  static Color of(Style style) {
    return STYLE[style.ordinal()];
  }
}
