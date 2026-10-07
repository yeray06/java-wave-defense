package io.github.yeray06.wavedefense.game.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EditorDocumentTest {
  private static void type(EditorDocument doc, String text) {
    for (char c : text.toCharArray()) {
      if (c == '\n') {
        doc.newline();
      } else {
        doc.insert(String.valueOf(c));
      }
    }
  }

  @Test
  void typingAnIfBlockIndentsAndClosesAtTheRightLevel() {
    EditorDocument doc = new EditorDocument("");
    type(doc, "if (hayEnemigos()) {\ndisparar();\n}");
    assertEquals("if (hayEnemigos()) {\n  disparar();\n}", doc.text());
    assertEquals(2, doc.cursorLine());
    assertEquals(1, doc.cursorColumn());
  }

  @Test
  void backspaceAndDeleteJoinLines() {
    EditorDocument doc = new EditorDocument("ab\ncd");
    doc.moveTo(1, 0, false);
    doc.backspace();
    assertEquals("abcd", doc.text());
    assertEquals(2, doc.cursorColumn());
    doc.end(false);
    doc.insert("\nx");
    doc.moveTo(0, 4, false);
    doc.delete();
    assertEquals("abcdx", doc.text());
    doc.moveTo(0, 0, false);
    doc.backspace();
    assertEquals("abcdx", doc.text(), "nothing before the start");
  }

  @Test
  void selectionIsReplacedCutAndCopied() {
    EditorDocument doc = new EditorDocument("disparar();\nrecargar();");
    doc.moveTo(0, 0, false);
    doc.moveTo(1, 3, true);
    assertTrue(doc.hasSelection());
    assertEquals("disparar();\nrec", doc.selectedText());
    doc.insert("X");
    assertEquals("Xargar();", doc.text());
    doc.selectAll();
    assertEquals("Xargar();", doc.cut());
    assertEquals("", doc.text());
    doc.insert("a\nb");
    assertEquals("a\nb", doc.text());
    assertEquals(1, doc.cursorLine());
  }

  @Test
  void movementKeysStayInsideTheText() {
    EditorDocument doc = new EditorDocument("  if (x) {\nab\n}");
    doc.moveTo(0, 8, false);
    doc.down(false);
    assertEquals(1, doc.cursorLine());
    assertEquals(2, doc.cursorColumn(), "clamped to the short line");
    doc.right(false);
    assertEquals(2, doc.cursorLine());
    doc.left(false);
    doc.left(false);
    assertEquals(1, doc.cursorColumn());
    doc.moveTo(0, 6, false);
    doc.home(false);
    assertEquals(2, doc.cursorColumn(), "home goes to the code first");
    doc.home(false);
    assertEquals(0, doc.cursorColumn(), "then to the margin");
    doc.end(true);
    assertEquals("  if (x) {", doc.selectedText());
    doc.left(false);
    assertFalse(doc.hasSelection());
    assertEquals(0, doc.cursorColumn(), "left collapses to the selection start");
  }

  @Test
  void undoAndRedoRestoreTextAndTypingIsOneStep() {
    EditorDocument doc = new EditorDocument("");
    type(doc, "disparar();");
    doc.newline();
    type(doc, "x");
    assertTrue(doc.undo());
    assertEquals("disparar();\n", doc.text());
    assertTrue(doc.undo());
    assertEquals("disparar();", doc.text());
    assertTrue(doc.undo());
    assertEquals("", doc.text(), "the whole word was one step");
    assertFalse(doc.undo());
    assertTrue(doc.redo());
    assertEquals("disparar();", doc.text());
  }

  @Test
  void tabsBecomeSpacesAndVersionMovesOnEveryChange() {
    EditorDocument doc = new EditorDocument("a\tb");
    assertEquals("a  b", doc.text());
    int version = doc.version();
    doc.moveTo(0, 0, false);
    doc.tab();
    assertEquals("  a  b", doc.text());
    assertTrue(doc.version() > version);
  }
}
