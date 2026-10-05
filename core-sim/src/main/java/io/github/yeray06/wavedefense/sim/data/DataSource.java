package io.github.yeray06.wavedefense.sim.data;

import java.io.IOException;

/** Reads a data file by its path relative to the assets folder. The game and the CLI differ. */
@FunctionalInterface
public interface DataSource {
  String read(String relativePath) throws IOException;
}
