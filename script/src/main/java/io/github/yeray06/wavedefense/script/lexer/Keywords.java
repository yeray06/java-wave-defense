package io.github.yeray06.wavedefense.script.lexer;

import java.util.Map;
import java.util.Set;

/** Java's reserved words, including the ones the game does not support yet. */
public final class Keywords {

  /** Words with their own token kind. */
  static final Map<String, TokenKind> KINDS =
      Map.ofEntries(
          Map.entry("if", TokenKind.IF),
          Map.entry("else", TokenKind.ELSE),
          Map.entry("while", TokenKind.WHILE),
          Map.entry("for", TokenKind.FOR),
          Map.entry("do", TokenKind.DO),
          Map.entry("try", TokenKind.TRY),
          Map.entry("catch", TokenKind.CATCH),
          Map.entry("finally", TokenKind.FINALLY),
          Map.entry("class", TokenKind.CLASS),
          Map.entry("interface", TokenKind.CLASS),
          Map.entry("enum", TokenKind.CLASS),
          Map.entry("return", TokenKind.RETURN),
          Map.entry("new", TokenKind.NEW),
          Map.entry("this", TokenKind.THIS),
          Map.entry("super", TokenKind.THIS),
          Map.entry("void", TokenKind.VOID),
          Map.entry("int", TokenKind.INT),
          Map.entry("boolean", TokenKind.BOOLEAN),
          Map.entry("true", TokenKind.TRUE),
          Map.entry("false", TokenKind.FALSE),
          Map.entry("null", TokenKind.NULL));

  /** Primitive types the game does not have. */
  static final Set<String> OTHER_PRIMITIVES =
      Set.of("byte", "short", "long", "float", "double", "char");

  /** Modifiers: they only make sense on methods and classes. */
  static final Set<String> MODIFIERS =
      Set.of(
          "public",
          "private",
          "protected",
          "static",
          "final",
          "abstract",
          "native",
          "synchronized",
          "transient",
          "volatile",
          "strictfp");

  /** Every other reserved word: valid Java, not available in the game. */
  static final Set<String> OTHERS =
      Set.of(
          "assert",
          "break",
          "case",
          "const",
          "continue",
          "default",
          "extends",
          "goto",
          "implements",
          "import",
          "instanceof",
          "package",
          "switch",
          "throw",
          "throws");

  private Keywords() {}

  /** Whether {@code word} cannot be used as a name in Java. */
  public static boolean isReserved(String word) {
    return KINDS.containsKey(word)
        || OTHER_PRIMITIVES.contains(word)
        || MODIFIERS.contains(word)
        || OTHERS.contains(word);
  }
}
