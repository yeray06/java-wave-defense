package io.github.yeray06.wavedefense.script.check;

import java.util.Collection;
import java.util.Locale;

/**
 * «¿Quisiste decir…?»: the known name closest to one the player mistyped, by edit distance. Only
 * close names count, so a made-up name gets no suggestion.
 */
final class Suggestions {
  private Suggestions() {}

  /** The best candidate for {@code name}, or {@code ""} if none is close enough. */
  static String closest(String name, Collection<String> candidates) {
    String best = "";
    int bestDistance = Integer.MAX_VALUE;
    String lower = name.toLowerCase(Locale.ROOT);
    for (String candidate : candidates) {
      if (candidate.equals(name)) {
        continue;
      }
      int distance = distance(lower, candidate.toLowerCase(Locale.ROOT));
      if (distance < bestDistance || (distance == bestDistance && candidate.compareTo(best) < 0)) {
        best = candidate;
        bestDistance = distance;
      }
    }
    return bestDistance <= allowed(name.length()) ? best : "";
  }

  /** Typos allowed: one in a short name, more in a long one. Case alone counts as none. */
  private static int allowed(int length) {
    if (length <= 4) {
      return 1;
    }
    return length <= 8 ? 2 : 3;
  }

  /** Levenshtein distance, plus swapping two neighbours as one typo. */
  static int distance(String a, String b) {
    int[][] d = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) {
      d[i][0] = i;
    }
    for (int j = 0; j <= b.length(); j++) {
      d[0][j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
        if (i > 1
            && j > 1
            && a.charAt(i - 1) == b.charAt(j - 2)
            && a.charAt(i - 2) == b.charAt(j - 1)) {
          d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
        }
      }
    }
    return d[a.length()][b.length()];
  }
}
