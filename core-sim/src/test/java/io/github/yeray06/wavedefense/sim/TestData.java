package io.github.yeray06.wavedefense.sim;

import io.github.yeray06.wavedefense.script.CompileResult;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import io.github.yeray06.wavedefense.sim.data.GameData;
import java.nio.file.Files;
import java.nio.file.Path;

/** The shipped data, loaded once per test JVM (tests run with assets/ as working directory). */
public final class TestData {
  private static GameData data;

  private TestData() {}

  public static synchronized GameData load() {
    if (data == null) {
      data = GameData.load(file -> Files.readString(Path.of(file)));
    }
    return data;
  }

  /** Compiles with every MVP piece and wave 10 reached; fails the test on errors. */
  public static CompiledScript script(String source) {
    CompileResult result =
        load()
            .compiler()
            .compile(
                source,
                Unlocks.of(
                    10, Feature.IF_ELSE, Feature.VARIABLES, Feature.LOGIC_OPS, Feature.WHILE),
                100);
    if (!result.succeeded()) {
      throw new AssertionError("script does not compile: " + result.errors());
    }
    return result.script().orElseThrow();
  }
}
