package io.github.yeray06.wavedefense.game.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.yeray06.wavedefense.game.editor.Highlighter.Span;
import io.github.yeray06.wavedefense.game.editor.Highlighter.Style;
import java.util.List;
import org.junit.jupiter.api.Test;

class HighlighterTest {
  private static Style styleAt(List<List<Span>> spans, int line, int column) {
    for (Span span : spans.get(line)) {
      if (column >= span.start() && column < span.end()) {
        return span.style();
      }
    }
    return null;
  }

  @Test
  void coloursKeywordsCallsTypesNumbersConstantsAndComments() {
    String source =
        "// apunta\nEnemigo e = enemigoMasCercano();\nif (municion() == 0) recargar();\n"
            + "usarMunicion(PERFORANTE); int x = 3; /* fin */\nboolean b = true;";
    List<List<Span>> spans = Highlighter.highlight(source);
    assertEquals(5, spans.size());
    assertEquals(Style.COMMENT, styleAt(spans, 0, 3));
    assertEquals(Style.TYPE, styleAt(spans, 1, 0));
    assertEquals(Style.PLAIN, styleAt(spans, 1, 8));
    assertEquals(Style.CALL, styleAt(spans, 1, 12));
    assertEquals(Style.KEYWORD, styleAt(spans, 2, 0));
    assertEquals(Style.CALL, styleAt(spans, 2, 4));
    assertEquals(Style.NUMBER, styleAt(spans, 2, 18));
    assertEquals(Style.CONSTANT, styleAt(spans, 3, 13));
    assertEquals(Style.TYPE, styleAt(spans, 3, 26));
    assertEquals(Style.COMMENT, styleAt(spans, 3, 39));
    assertEquals(Style.CONSTANT, styleAt(spans, 4, 12));
  }

  @Test
  void brokenCodeStillGetsColoursWithoutThrowing() {
    List<List<Span>> spans = Highlighter.highlight("if (x == 1.5 { \"hola\"\n/* sin cerrar");
    assertEquals(Style.KEYWORD, styleAt(spans, 0, 0));
    assertEquals(Style.NUMBER, styleAt(spans, 0, 9));
    assertEquals(2, spans.size());
  }
}
