package io.github.yeray06.wavedefense.game.log;

import com.badlogic.gdx.ApplicationLogger;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * The game's log (ARCHITECTURE §7): {@code java.util.logging} into {@code logs/game-N.log} in the
 * user folder, 5 files of 1 MB that rotate, so a player can send it when something breaks. libGDX's
 * own log goes there too, and so does any exception nobody caught.
 */
public final class GameLog {
  private static final String NAME = "wave-defense";
  private static final int FILE_BYTES = 1024 * 1024;
  private static final int FILES = 5;
  private static final String FORMAT = "%1$tF %1$tT %4$s %3$s: %5$s%6$s%n";

  private final Logger logger;
  private final FileHandler file;

  private GameLog(Logger logger, FileHandler file) {
    this.logger = logger;
    this.file = file;
  }

  public Logger logger() {
    return logger;
  }

  /**
   * Closes the file: Windows cannot move or delete a log the game still holds, and the handler
   * would outlive the game in the shared {@code wave-defense} logger.
   */
  public void close() {
    if (file != null) {
      logger.removeHandler(file);
      file.close();
    }
  }

  /**
   * Opens the log in {@code folder} and catches uncaught exceptions on every thread. If the folder
   * cannot be written the game still runs, logging to the console only.
   */
  public static GameLog install(Path folder) {
    Logger logger = Logger.getLogger(NAME);
    FileHandler file = null;
    try {
      Files.createDirectories(folder);
      String pattern = folder.resolve("game-%g.log").toString().replace("%", "%%");
      pattern = pattern.replace("%%g.log", "%g.log");
      file = new FileHandler(pattern, FILE_BYTES, FILES, true);
      file.setFormatter(
          new SimpleFormatter() {
            @Override
            public String format(LogRecord record) {
              return String.format(
                  FORMAT,
                  record.getInstant().atZone(ZoneId.systemDefault()),
                  record.getSourceClassName(),
                  record.getLoggerName(),
                  record.getLevel().getName(),
                  formatMessage(record),
                  record.getThrown() == null ? "" : "\n" + stackTrace(record.getThrown()));
            }
          });
      logger.addHandler(file);
    } catch (IOException | SecurityException e) {
      logger.log(Level.WARNING, "Cannot open the log file in " + folder, e);
    }
    Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, error) -> {
          logger.log(Level.SEVERE, "Uncaught on " + thread.getName(), error);
          if (previous != null) {
            previous.uncaughtException(thread, error);
          }
        });
    return new GameLog(logger, file);
  }

  /** libGDX's {@code Gdx.app.log} and friends, into the same log. */
  public static ApplicationLogger forLibgdx(Logger logger) {
    return new ApplicationLogger() {
      @Override
      public void log(String tag, String message) {
        logger.info(tag + ": " + message);
      }

      @Override
      public void log(String tag, String message, Throwable exception) {
        logger.log(Level.INFO, tag + ": " + message, exception);
      }

      @Override
      public void error(String tag, String message) {
        logger.severe(tag + ": " + message);
      }

      @Override
      public void error(String tag, String message, Throwable exception) {
        logger.log(Level.SEVERE, tag + ": " + message, exception);
      }

      @Override
      public void debug(String tag, String message) {
        logger.fine(tag + ": " + message);
      }

      @Override
      public void debug(String tag, String message, Throwable exception) {
        logger.log(Level.FINE, tag + ": " + message, exception);
      }
    };
  }

  private static String stackTrace(Throwable thrown) {
    StringWriter out = new StringWriter();
    thrown.printStackTrace(new PrintWriter(out));
    return out.toString().stripTrailing();
  }
}
