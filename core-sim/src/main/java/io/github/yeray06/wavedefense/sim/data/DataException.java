package io.github.yeray06.wavedefense.sim.data;

/** A data file is missing, malformed or breaks a rule. Names the file and what is wrong. */
public final class DataException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final String file;

  public DataException(String file, String problem) {
    super(file + ": " + problem);
    this.file = file;
  }

  public DataException(String file, String problem, Throwable cause) {
    super(file + ": " + problem, cause);
    this.file = file;
  }

  public String file() {
    return file;
  }
}
