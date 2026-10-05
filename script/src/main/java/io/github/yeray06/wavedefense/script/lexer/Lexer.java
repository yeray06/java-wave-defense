package io.github.yeray06.wavedefense.script.lexer;

import io.github.yeray06.wavedefense.script.diag.Diagnostics;
import io.github.yeray06.wavedefense.script.diag.ErrorCode;
import java.util.ArrayList;
import java.util.List;

/** Splits source text into tokens. Lines and columns are 1-based; a tab counts as one column. */
public final class Lexer {
  /** One past {@code Integer.MAX_VALUE}: the only literal that needs a unary minus to be valid. */
  public static final long INT_LITERAL_LIMIT = 2_147_483_648L;

  /** Longest first, so {@code >>>=} wins over {@code >>}. */
  private static final String[] UNSUPPORTED_OPERATORS = {
    ">>>=", "<<=", ">>=", ">>>", "...", "&=", "|=", "^=", "<<", ">>", "->", "::", "&", "|", "^",
    "~", "?", ":", "@"
  };

  private final String source;
  private final Diagnostics diagnostics;
  private final List<Token> tokens = new ArrayList<>();
  private int pos;
  private int line = 1;
  private int column = 1;

  private Lexer(String source, Diagnostics diagnostics) {
    this.source = source;
    this.diagnostics = diagnostics;
  }

  public static List<Token> tokenize(String source, Diagnostics diagnostics) {
    Lexer lexer = new Lexer(source, diagnostics);
    lexer.run();
    return List.copyOf(lexer.tokens);
  }

  private void run() {
    while (true) {
      skipWhitespaceAndComments();
      if (pos >= source.length()) {
        tokens.add(new Token(TokenKind.EOF, "", line, column, 0));
        return;
      }
      int startLine = line;
      int startColumn = column;
      int start = pos;
      char c = source.charAt(pos);
      if (Character.isJavaIdentifierStart(c)) {
        word(start, startLine, startColumn);
      } else if (isDigit(c) || (c == '.' && isDigit(peek(1)))) {
        number(start, startLine, startColumn);
      } else if (c == '"' || c == '\'') {
        quoted(c, startLine, startColumn);
      } else {
        symbol(startLine, startColumn);
      }
    }
  }

  private void skipWhitespaceAndComments() {
    while (pos < source.length()) {
      char c = source.charAt(pos);
      if (c == '/' && peek(1) == '/') {
        while (pos < source.length() && !isLineBreak(source.charAt(pos))) {
          advance();
        }
      } else if (c == '/' && peek(1) == '*') {
        int startLine = line;
        int startColumn = column;
        advance();
        advance();
        while (pos < source.length() && !(source.charAt(pos) == '*' && peek(1) == '/')) {
          advance();
        }
        if (pos >= source.length()) {
          diagnostics.report(ErrorCode.UNTERMINATED_COMMENT, startLine, startColumn);
          return;
        }
        advance();
        advance();
      } else if (Character.isWhitespace(c)) {
        advance();
      } else {
        return;
      }
    }
  }

  private void word(int start, int startLine, int startColumn) {
    while (pos < source.length() && Character.isJavaIdentifierPart(source.charAt(pos))) {
      advance();
    }
    String text = source.substring(start, pos);
    TokenKind kind = Keywords.KINDS.get(text);
    if (kind == null) {
      if (Keywords.OTHER_PRIMITIVES.contains(text)) {
        kind = TokenKind.OTHER_PRIMITIVE;
      } else if (Keywords.MODIFIERS.contains(text)) {
        kind = TokenKind.MODIFIER;
      } else if (Keywords.OTHERS.contains(text)) {
        kind = TokenKind.OTHER_KEYWORD;
      } else {
        kind = TokenKind.IDENTIFIER;
      }
    }
    tokens.add(new Token(kind, text, startLine, startColumn, 0));
  }

  private void number(int start, int startLine, int startColumn) {
    boolean radixPrefix =
        source.charAt(pos) == '0' && "xXbB".indexOf(peek(1)) >= 0 && peek(1) != '\0';
    boolean decimal = source.charAt(pos) == '.';
    while (pos < source.length()) {
      char c = source.charAt(pos);
      if (isDigit(c) || c == '_' || Character.isLetter(c)) {
        boolean exponentSign =
            (c == 'e' || c == 'E') && !radixPrefix && (peek(1) == '+' || peek(1) == '-');
        decimal |= !radixPrefix && (c == 'e' || c == 'E' || "fFdD".indexOf(c) >= 0);
        advance();
        if (exponentSign) {
          advance();
        }
      } else if (c == '.' && !radixPrefix && isDigit(peek(1))) {
        decimal = true;
        advance();
      } else {
        break;
      }
    }
    String text = source.substring(start, pos);
    if (decimal) {
      error(ErrorCode.DECIMAL_NOT_SUPPORTED, text, startLine, startColumn);
      return;
    }
    String digits = text.replace("_", "");
    boolean plainDecimal =
        !radixPrefix
            && !text.endsWith("_")
            && digits.chars().allMatch(Lexer::isDigit)
            && (digits.length() == 1 || digits.charAt(0) != '0');
    if (!plainDecimal) {
      error(ErrorCode.NUMBER_FORMAT_NOT_SUPPORTED, text, startLine, startColumn);
      return;
    }
    if (digits.length() > 10 || Long.parseLong(digits) > INT_LITERAL_LIMIT) {
      error(ErrorCode.INTEGER_TOO_LARGE, text, startLine, startColumn);
      return;
    }
    tokens.add(
        new Token(TokenKind.INT_LITERAL, text, startLine, startColumn, Long.parseLong(digits)));
  }

  private void quoted(char quote, int startLine, int startColumn) {
    int start = pos;
    advance();
    while (pos < source.length() && !isLineBreak(source.charAt(pos))) {
      char c = source.charAt(pos);
      advance();
      if (c == '\\' && pos < source.length() && !isLineBreak(source.charAt(pos))) {
        advance();
      } else if (c == quote) {
        break;
      }
    }
    ErrorCode code = quote == '"' ? ErrorCode.STRING_NOT_SUPPORTED : ErrorCode.CHAR_NOT_SUPPORTED;
    diagnostics.report(code, startLine, startColumn);
    tokens.add(
        new Token(
            TokenKind.ERROR_LITERAL, source.substring(start, pos), startLine, startColumn, 0));
  }

  private void symbol(int startLine, int startColumn) {
    // Before the unsupported list, which contains their single-character prefixes.
    if (source.startsWith("&&", pos)) {
      emit(TokenKind.AND_AND, "&&", startLine, startColumn);
      return;
    }
    if (source.startsWith("||", pos)) {
      emit(TokenKind.OR_OR, "||", startLine, startColumn);
      return;
    }
    for (String op : UNSUPPORTED_OPERATORS) {
      if (source.startsWith(op, pos)) {
        emit(TokenKind.UNSUPPORTED_OPERATOR, op, startLine, startColumn);
        return;
      }
    }
    char c = source.charAt(pos);
    char next = peek(1);
    TokenKind kind;
    String text;
    switch (c) {
      case '(' -> {
        kind = TokenKind.LPAREN;
        text = "(";
      }
      case ')' -> {
        kind = TokenKind.RPAREN;
        text = ")";
      }
      case '{' -> {
        kind = TokenKind.LBRACE;
        text = "{";
      }
      case '}' -> {
        kind = TokenKind.RBRACE;
        text = "}";
      }
      case '[' -> {
        kind = TokenKind.LBRACKET;
        text = "[";
      }
      case ']' -> {
        kind = TokenKind.RBRACKET;
        text = "]";
      }
      case ';' -> {
        kind = TokenKind.SEMICOLON;
        text = ";";
      }
      case ',' -> {
        kind = TokenKind.COMMA;
        text = ",";
      }
      case '.' -> {
        kind = TokenKind.DOT;
        text = ".";
      }
      case '=' -> {
        kind = next == '=' ? TokenKind.EQ : TokenKind.ASSIGN;
        text = next == '=' ? "==" : "=";
      }
      case '!' -> {
        kind = next == '=' ? TokenKind.NE : TokenKind.BANG;
        text = next == '=' ? "!=" : "!";
      }
      case '<' -> {
        kind = next == '=' ? TokenKind.LE : TokenKind.LT;
        text = next == '=' ? "<=" : "<";
      }
      case '>' -> {
        kind = next == '=' ? TokenKind.GE : TokenKind.GT;
        text = next == '=' ? ">=" : ">";
      }
      case '+' -> {
        kind =
            next == '+'
                ? TokenKind.PLUS_PLUS
                : next == '=' ? TokenKind.PLUS_ASSIGN : TokenKind.PLUS;
        text = next == '+' ? "++" : next == '=' ? "+=" : "+";
      }
      case '-' -> {
        kind =
            next == '-'
                ? TokenKind.MINUS_MINUS
                : next == '=' ? TokenKind.MINUS_ASSIGN : TokenKind.MINUS;
        text = next == '-' ? "--" : next == '=' ? "-=" : "-";
      }
      case '*' -> {
        kind = next == '=' ? TokenKind.STAR_ASSIGN : TokenKind.STAR;
        text = next == '=' ? "*=" : "*";
      }
      case '/' -> {
        kind = next == '=' ? TokenKind.SLASH_ASSIGN : TokenKind.SLASH;
        text = next == '=' ? "/=" : "/";
      }
      case '%' -> {
        kind = next == '=' ? TokenKind.PERCENT_ASSIGN : TokenKind.PERCENT;
        text = next == '=' ? "%=" : "%";
      }
      default -> {
        diagnostics.report(
            ErrorCode.UNEXPECTED_CHARACTER, startLine, startColumn, String.valueOf(c));
        advance();
        return;
      }
    }
    emit(kind, text, startLine, startColumn);
  }

  private void emit(TokenKind kind, String text, int startLine, int startColumn) {
    for (int i = 0; i < text.length(); i++) {
      advance();
    }
    tokens.add(new Token(kind, text, startLine, startColumn, 0));
  }

  private void error(ErrorCode code, String text, int startLine, int startColumn) {
    diagnostics.report(code, startLine, startColumn, text);
    tokens.add(new Token(TokenKind.ERROR_LITERAL, text, startLine, startColumn, 0));
  }

  private void advance() {
    char c = source.charAt(pos++);
    if (c == '\n' || (c == '\r' && peek(0) != '\n')) {
      line++;
      column = 1;
    } else if (c != '\r') {
      column++;
    }
  }

  private char peek(int offset) {
    int index = pos + offset;
    return index < source.length() ? source.charAt(index) : '\0';
  }

  private static boolean isDigit(int c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isLineBreak(char c) {
    return c == '\n' || c == '\r';
  }
}
