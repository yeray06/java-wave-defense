package io.github.yeray06.wavedefense.platform;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * Resolves the per-user folder for saves, settings and logs. The locations match roots that Steam
 * Auto-Cloud supports (WinAppDataRoaming, MacAppSupport, LinuxXdgDataHome).
 */
public final class UserDataDirectory {
  private final String windowsFolderName;
  private final String unixFolderName;

  /**
   * @param windowsFolderName folder name on Windows and macOS (e.g. {@code JavaWaveDefense})
   * @param unixFolderName folder name on Linux (e.g. {@code java-wave-defense})
   */
  public UserDataDirectory(String windowsFolderName, String unixFolderName) {
    this.windowsFolderName = windowsFolderName;
    this.unixFolderName = unixFolderName;
  }

  /** Folder for the running machine. */
  public Path resolveCurrent() {
    return resolve(OsFamily.current(), System::getenv, Path.of(System.getProperty("user.home")));
  }

  /**
   * Folder for the given OS. Pure: environment and home are passed in so every OS can be tested
   * from any machine.
   */
  public Path resolve(OsFamily os, Function<String, String> env, Path home) {
    return switch (os) {
      case WINDOWS -> {
        String appData = env.apply("APPDATA");
        Path base = isSet(appData) ? Path.of(appData) : home.resolve("AppData").resolve("Roaming");
        yield base.resolve(windowsFolderName);
      }
      case MACOS ->
          home.resolve("Library").resolve("Application Support").resolve(windowsFolderName);
      case LINUX -> {
        String xdgDataHome = env.apply("XDG_DATA_HOME");
        // The XDG spec says relative values are invalid and must be ignored.
        Path base =
            isSet(xdgDataHome) && Path.of(xdgDataHome).isAbsolute()
                ? Path.of(xdgDataHome)
                : home.resolve(".local").resolve("share");
        yield base.resolve(unixFolderName);
      }
    };
  }

  private static boolean isSet(String value) {
    return value != null && !value.isBlank();
  }
}
