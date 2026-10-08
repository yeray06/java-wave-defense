package io.github.yeray06.wavedefense.game.editor;

import io.github.yeray06.wavedefense.script.diag.Diagnostics;
import io.github.yeray06.wavedefense.script.lexer.Lexer;
import io.github.yeray06.wavedefense.script.lexer.Token;
import io.github.yeray06.wavedefense.script.lexer.TokenKind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Autocompletion for the code editor (D-66): what can be written where the cursor is. Words come
 * from the caller (calls, keywords and constants the player owns) and from the code itself (the
 * variables and methods it declares, read with the interpreter's lexer). After a dot it offers the
 * enemy's calls. Matching ignores case; words that start with the prefix come before words that
 * only contain it.
 */
public final class Completer {
  /** At most this many suggestions are shown. */
  public static final int MAX = 8;

  private Completer() {}

  /**
   * What is being written at the cursor.
   *
   * @param prefix the identifier's characters before the cursor
   * @param start the 0-based column where it starts: a suggestion replaces from there
   * @param member whether it follows a dot
   */
  public record Context(String prefix, int start, boolean member) {}

  /** The word being written at {@code column} of {@code line}. */
  public static Context context(CharSequence line, int column) {
    int start = Math.min(column, line.length());
    while (start > 0 && Character.isJavaIdentifierPart(line.charAt(start - 1))) {
      start--;
    }
    int before = start - 1;
    while (before >= 0 && line.charAt(before) == ' ') {
      before--;
    }
    boolean member = before >= 0 && line.charAt(before) == '.';
    return new Context(
        line.subSequence(start, Math.min(column, line.length())).toString(), start, member);
  }

  /**
   * Suggestions for {@code context}: words ending in {@code (} or {@code ()} are calls.
   *
   * @param words what can be written anywhere
   * @param members what can be written after a dot
   * @param code the whole text, for the names it declares
   */
  public static List<String> suggest(
      Context context, Collection<String> words, Collection<String> members, String code) {
    if (context.prefix().isEmpty() && !context.member()) {
      return List.of();
    }
    Set<String> pool = new LinkedHashSet<>();
    if (context.member()) {
      pool.addAll(members);
    } else {
      pool.addAll(declared(code));
      pool.addAll(words);
    }
    String prefix = context.prefix().toLowerCase(Locale.ROOT);
    List<String> starts = new ArrayList<>();
    List<String> contains = new ArrayList<>();
    for (String word : pool) {
      String name = name(word).toLowerCase(Locale.ROOT);
      if (name.equals(prefix)) {
        continue;
      }
      if (name.startsWith(prefix)) {
        starts.add(word);
      } else if (prefix.length() >= 2 && name.contains(prefix)) {
        contains.add(word);
      }
    }
    starts.sort(String.CASE_INSENSITIVE_ORDER);
    contains.sort(String.CASE_INSENSITIVE_ORDER);
    starts.addAll(contains);
    return starts.size() > MAX ? List.copyOf(starts.subList(0, MAX)) : List.copyOf(starts);
  }

  /** The identifier of a suggestion, without its parentheses. */
  public static String name(String word) {
    int paren = word.indexOf('(');
    return paren < 0 ? word : word.substring(0, paren);
  }

  /**
   * Variables, parameters and methods declared in {@code code}: an identifier right after a type
   * ({@code int}, {@code boolean}, {@code void}, a class name or {@code List<…>}). Methods come
   * with {@code (}.
   */
  public static Set<String> declared(String code) {
    List<Token> tokens = Lexer.tokenize(code, new Diagnostics());
    Set<String> names = new LinkedHashSet<>();
    for (int i = 1; i < tokens.size(); i++) {
      Token token = tokens.get(i);
      if (token.kind() != TokenKind.IDENTIFIER) {
        continue;
      }
      Token type = tokens.get(i - 1);
      boolean afterType =
          switch (type.kind()) {
            case INT, BOOLEAN, VOID, GT -> true;
            case IDENTIFIER -> Character.isUpperCase(type.text().charAt(0));
            default -> false;
          };
      if (!afterType) {
        continue;
      }
      boolean method = i + 1 < tokens.size() && tokens.get(i + 1).kind() == TokenKind.LPAREN;
      names.add(method ? token.text() + "(" : token.text());
    }
    return names;
  }
}
