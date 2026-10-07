package io.github.yeray06.wavedefense.game.editor;

import io.github.yeray06.wavedefense.script.diag.Diagnostics;
import io.github.yeray06.wavedefense.script.lexer.Lexer;
import io.github.yeray06.wavedefense.script.lexer.Token;
import io.github.yeray06.wavedefense.script.lexer.TokenKind;
import java.util.ArrayList;
import java.util.List;

/**
 * Colours for the editor, from the interpreter's own lexer: what is highlighted as a keyword is
 * exactly what the game reads as one. The lexer drops comments, so any non-blank text between
 * tokens is shown as a comment.
 */
public final class Highlighter {
  /** What a piece of text is, for its colour. */
  public enum Style {
    PLAIN,
    KEYWORD,
    TYPE,
    CALL,
    NUMBER,
    CONSTANT,
    COMMENT
  }

  /** A coloured run on one line, columns 0-based, end exclusive. */
  public record Span(int start, int end, Style style) {}

  private static final String ENEMY_TYPE = "Enemigo";

  private Highlighter() {}

  /** Spans for every line of {@code source}, in order, covering every non-blank character. */
  public static List<List<Span>> highlight(String source) {
    String[] lines = source.split("\n", -1);
    List<List<Span>> out = new ArrayList<>(lines.length);
    for (int i = 0; i < lines.length; i++) {
      out.add(new ArrayList<>());
    }
    List<Token> tokens = Lexer.tokenize(source, new Diagnostics());
    int[] covered = new int[lines.length];
    for (int t = 0; t < tokens.size(); t++) {
      Token token = tokens.get(t);
      if (token.kind() == TokenKind.EOF) {
        break;
      }
      int line = token.line() - 1;
      if (line < 0 || line >= lines.length) {
        continue;
      }
      int start = token.column() - 1;
      int end = Math.min(lines[line].length(), start + token.text().length());
      Token next = t + 1 < tokens.size() ? tokens.get(t + 1) : null;
      addGap(out.get(line), lines[line], covered[line], start);
      out.get(line).add(new Span(start, end, style(token, next)));
      covered[line] = end;
    }
    for (int i = 0; i < lines.length; i++) {
      addGap(out.get(i), lines[i], covered[i], lines[i].length());
    }
    return out;
  }

  /** Text the lexer skipped: blank, or a comment. */
  private static void addGap(List<Span> spans, String line, int from, int to) {
    if (to > from && !line.substring(from, to).isBlank()) {
      int start = from;
      while (line.charAt(start) == ' ') {
        start++;
      }
      spans.add(new Span(start, to, Style.COMMENT));
    }
  }

  private static Style style(Token token, Token next) {
    return switch (token.kind()) {
      case IF,
          ELSE,
          WHILE,
          FOR,
          DO,
          TRY,
          CATCH,
          FINALLY,
          CLASS,
          RETURN,
          NEW,
          THIS,
          VOID,
          MODIFIER,
          OTHER_KEYWORD ->
          Style.KEYWORD;
      case INT, BOOLEAN, OTHER_PRIMITIVE -> Style.TYPE;
      case TRUE, FALSE, NULL -> Style.CONSTANT;
      case INT_LITERAL, ERROR_LITERAL -> Style.NUMBER;
      case IDENTIFIER -> identifier(token.text(), next);
      default -> Style.PLAIN;
    };
  }

  private static Style identifier(String text, Token next) {
    if (next != null && next.kind() == TokenKind.LPAREN) {
      return Style.CALL;
    }
    if (text.equals(ENEMY_TYPE)) {
      return Style.TYPE;
    }
    if (text.equals(text.toUpperCase(java.util.Locale.ROOT))
        && !text.equals(text.toLowerCase(java.util.Locale.ROOT))) {
      return Style.CONSTANT;
    }
    return Style.PLAIN;
  }
}
