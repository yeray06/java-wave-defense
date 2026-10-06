package io.github.yeray06.wavedefense.game;

import io.github.yeray06.wavedefense.sim.data.GameData;
import java.nio.file.Files;
import java.nio.file.Path;

/** The shipped data (tests run with assets/ as working directory). */
public final class GameTestData {
  private static GameData data;

  private GameTestData() {}

  public static synchronized GameData load() {
    if (data == null) {
      data = GameData.load(file -> Files.readString(Path.of(file)));
    }
    return data;
  }
}
