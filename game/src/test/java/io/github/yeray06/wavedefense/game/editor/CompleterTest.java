package io.github.yeray06.wavedefense.game.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.game.GameTestData;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** D-66: what the editor offers at the cursor. */
class CompleterTest {
  private static final List<String> WORDS =
      List.of("disparar()", "recargar()", "apuntar(", "municion()", "while", "int");
  private static final List<String> MEMBERS = List.of("vida()", "blindado()", "mutante()");

  @Test
  void theWordAtTheCursorAndWhetherItFollowsADot() {
    assertEquals(new Completer.Context("dis", 2, false), Completer.context("  dis", 5));
    assertEquals(new Completer.Context("vi", 2, true), Completer.context("e.vi", 4));
    assertEquals(new Completer.Context("", 6, true), Completer.context("enemy. ", 6));
  }

  @Test
  void prefixesComeFirstAndCaseIsIgnored() {
    List<String> found = Completer.suggest(Completer.context("Re", 2), WORDS, MEMBERS, "");
    assertEquals(List.of("recargar()"), found);
    assertEquals(
        List.of("municion()"), Completer.suggest(Completer.context("cion", 4), WORDS, MEMBERS, ""));
  }

  @Test
  void afterADotOnlyTheEnemysCalls() {
    List<String> found = Completer.suggest(Completer.context("e.", 2), WORDS, MEMBERS, "");
    assertEquals(List.of("blindado()", "mutante()", "vida()"), found);
  }

  @Test
  void theCodesOwnVariablesAndMethodsAreOffered() {
    String code = "int disparos = 0;\nEnemigo objetivo = null;\nvoid rafaga(int veces) {\n}";
    assertEquals(Set.of("disparos", "objetivo", "rafaga(", "veces"), Completer.declared(code));
    assertTrue(
        Completer.suggest(Completer.context("obj", 3), WORDS, MEMBERS, code).contains("objetivo"));
  }

  @Test
  void nothingForAnEmptyWordOrAWordAlreadyComplete() {
    assertTrue(Completer.suggest(Completer.context("", 0), WORDS, MEMBERS, "").isEmpty());
    assertTrue(Completer.suggest(Completer.context("while", 5), WORDS, MEMBERS, "").isEmpty());
  }

  @Test
  void theVocabularyHasOnlyWhatThePlayerOwns() {
    var api = GameTestData.load().scriptApi().api();
    Vocabulary start = Vocabulary.of(api, Unlocks.of(1), "");
    assertTrue(start.words().contains("disparar()"));
    assertFalse(start.words().contains("while"));
    assertFalse(start.words().contains("int"));
    Vocabulary later =
        Vocabulary.of(
            api,
            Unlocks.of(
                16,
                Feature.IF_ELSE,
                Feature.VARIABLES,
                Feature.LOGIC_OPS,
                Feature.WHILE,
                Feature.FOR,
                Feature.METHODS),
            "void rafaga() {\n}");
    assertTrue(later.words().containsAll(List.of("while", "for", "rafaga(", "apuntar(")));
    assertTrue(later.members().contains("size()"));
  }
}
