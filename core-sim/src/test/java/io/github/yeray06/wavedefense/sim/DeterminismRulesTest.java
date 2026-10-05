package io.github.yeray06.wavedefense.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The simulation must give the same result on every machine (saves, replays, the balance report
 * checked on Linux and Windows CI). {@code Math.pow}, {@code exp}, {@code log}, {@code hypot} and
 * the trigonometric functions may use CPU intrinsics that differ in the last bit; {@code
 * StrictMath} may not.
 */
class DeterminismRulesTest {
  private static final Pattern NON_STRICT =
      Pattern.compile("(?<!Strict)Math\\.(pow|exp|log|log10|hypot|sin|cos|tan|atan2?|cbrt)\\(");
  private static final Pattern FORBIDDEN =
      Pattern.compile("System\\.(currentTimeMillis|nanoTime)|new Random\\(|Math\\.random\\(");

  @Test
  void simulationUsesStrictMathAndNoClockOrUnseededRandom() throws IOException {
    // Tests run from assets/; the sources are next to it.
    Path sources = Path.of("..", "core-sim", "src", "main", "java");
    List<String> offenders;
    try (Stream<Path> files = Files.walk(sources)) {
      offenders =
          files
              .filter(f -> f.toString().endsWith(".java"))
              .filter(DeterminismRulesTest::breaksTheRules)
              .map(Path::toString)
              .toList();
    }
    assertEquals(List.of(), offenders);
  }

  private static boolean breaksTheRules(Path file) {
    try {
      String code = Files.readString(file);
      return NON_STRICT.matcher(code).find() || FORBIDDEN.matcher(code).find();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
