package io.github.yeray06.wavedefense.script.lexer;

/**
 * One token. {@code value} is only meaningful for {@link TokenKind#INT_LITERAL}, where it may be
 * 2147483648 (valid only after a unary minus).
 */
public record Token(TokenKind kind, String text, int line, int column, long value) {

  /** How the token is named in error messages. */
  public String display() {
    return kind == TokenKind.EOF ? "EOF" : text;
  }
}
