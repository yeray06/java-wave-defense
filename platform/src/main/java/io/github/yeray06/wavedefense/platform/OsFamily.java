package io.github.yeray06.wavedefense.platform;

import java.util.Locale;

/** Desktop operating systems the game targets. */
public enum OsFamily {
  WINDOWS,
  MACOS,
  LINUX;

  /** Maps a {@code os.name} system property value to a family; anything unknown is Linux-like. */
  public static OsFamily fromOsName(String osName) {
    String name = osName.toLowerCase(Locale.ROOT);
    if (name.startsWith("windows")) {
      return WINDOWS;
    }
    if (name.startsWith("mac") || name.startsWith("darwin")) {
      return MACOS;
    }
    return LINUX;
  }

  public static OsFamily current() {
    return fromOsName(System.getProperty("os.name", ""));
  }
}
