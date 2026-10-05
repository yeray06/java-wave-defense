package io.github.yeray06.wavedefense.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.diag.Diagnostics;
import io.github.yeray06.wavedefense.script.lexer.Keywords;
import io.github.yeray06.wavedefense.script.lexer.Lexer;
import io.github.yeray06.wavedefense.script.lexer.Token;
import io.github.yeray06.wavedefense.script.lexer.TokenKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class LexerTest {

  private static List<TokenKind> kinds(String source) {
    return Lexer.tokenize(source, new Diagnostics()).stream().map(Token::kind).toList();
  }

  @Test
  void tokensCarryLineAndColumn() {
    List<Token> tokens = Lexer.tokenize("if (x)\n  disparar();", new Diagnostics());
    Token disparar = tokens.get(4);
    assertEquals("disparar", disparar.text());
    assertEquals(2, disparar.line());
    assertEquals(3, disparar.column());
  }

  @Test
  void windowsAndOldMacLineEndingsCountAsOneLine() {
    List<Token> crlf = Lexer.tokenize("a\r\nb", new Diagnostics());
    List<Token> cr = Lexer.tokenize("a\rb", new Diagnostics());
    assertEquals(2, crlf.get(1).line());
    assertEquals(1, crlf.get(1).column());
    assertEquals(2, cr.get(1).line());
  }

  @Test
  void commentsAreSkipped() {
    assertEquals(
        List.of(TokenKind.IDENTIFIER, TokenKind.SEMICOLON, TokenKind.EOF),
        kinds("// uno\n/* dos\n tres */ a; // cuatro"));
  }

  @Test
  void operatorsUseLongestMatch() {
    assertEquals(
        List.of(
            TokenKind.AND_AND,
            TokenKind.OR_OR,
            TokenKind.EQ,
            TokenKind.ASSIGN,
            TokenKind.LE,
            TokenKind.PLUS_PLUS,
            TokenKind.PLUS_ASSIGN,
            TokenKind.MINUS_MINUS,
            TokenKind.NE,
            TokenKind.BANG,
            TokenKind.PERCENT_ASSIGN,
            TokenKind.UNSUPPORTED_OPERATOR,
            TokenKind.UNSUPPORTED_OPERATOR,
            TokenKind.EOF),
        kinds("&& || == = <= ++ += -- != ! %= & >>>="));
  }

  @Test
  void javaKeywordsAreRecognizedEvenWhenNotSupported() {
    assertEquals(
        List.of(
            TokenKind.FOR,
            TokenKind.TRY,
            TokenKind.CLASS,
            TokenKind.MODIFIER,
            TokenKind.OTHER_PRIMITIVE,
            TokenKind.OTHER_KEYWORD,
            TokenKind.IDENTIFIER,
            TokenKind.EOF),
        kinds("for try class static double break disparar"));
    assertTrue(Keywords.isReserved("while"));
    assertTrue(Keywords.isReserved("goto"));
    assertFalse(Keywords.isReserved("disparar"));
  }

  @Test
  void intLiteralsAllowUnderscoresAndTheMinValueMagnitude() {
    List<Token> tokens = Lexer.tokenize("1_000 2147483648 0", new Diagnostics());
    assertEquals(1000, tokens.get(0).value());
    assertEquals(2147483648L, tokens.get(1).value());
    assertEquals(0, tokens.get(2).value());
  }

  @Test
  void unicodeIdentifiersWork() {
    List<Token> tokens = Lexer.tokenize("int daño = 1;", new Diagnostics());
    assertEquals("daño", tokens.get(1).text());
    assertTrue(Scripts.compile("int daño = 1;\ndaño++;").succeeded());
  }
}
