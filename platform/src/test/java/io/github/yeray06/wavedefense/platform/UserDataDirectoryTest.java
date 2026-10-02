package io.github.yeray06.wavedefense.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserDataDirectoryTest {
  private static final Path HOME = Path.of("/home/player");
  private final UserDataDirectory dirs =
      new UserDataDirectory("JavaWaveDefense", "java-wave-defense");

  @Test
  void windowsUsesAppData() {
    Path appData = HOME.resolve("roaming");
    Path dir = dirs.resolve(OsFamily.WINDOWS, Map.of("APPDATA", appData.toString())::get, HOME);
    assertEquals(appData.resolve("JavaWaveDefense"), dir);
  }

  @Test
  void windowsFallsBackWhenAppDataIsMissing() {
    Path dir = dirs.resolve(OsFamily.WINDOWS, Map.<String, String>of()::get, HOME);
    assertEquals(HOME.resolve("AppData").resolve("Roaming").resolve("JavaWaveDefense"), dir);
  }

  @Test
  void macOsUsesApplicationSupport() {
    Path dir = dirs.resolve(OsFamily.MACOS, Map.<String, String>of()::get, HOME);
    assertEquals(
        HOME.resolve("Library").resolve("Application Support").resolve("JavaWaveDefense"), dir);
  }

  @Test
  void linuxUsesXdgDataHomeWhenAbsolute() {
    Path xdg = HOME.resolve("xdg").toAbsolutePath();
    Path dir = dirs.resolve(OsFamily.LINUX, Map.of("XDG_DATA_HOME", xdg.toString())::get, HOME);
    assertEquals(xdg.resolve("java-wave-defense"), dir);
  }

  @Test
  void linuxIgnoresRelativeXdgDataHome() {
    Path dir = dirs.resolve(OsFamily.LINUX, Map.of("XDG_DATA_HOME", "relative/dir")::get, HOME);
    assertEquals(HOME.resolve(".local").resolve("share").resolve("java-wave-defense"), dir);
  }

  @Test
  void linuxDefaultsToLocalShare() {
    Path dir = dirs.resolve(OsFamily.LINUX, Map.<String, String>of()::get, HOME);
    assertEquals(HOME.resolve(".local").resolve("share").resolve("java-wave-defense"), dir);
  }

  @Test
  void osNamesMapToFamilies() {
    assertEquals(OsFamily.WINDOWS, OsFamily.fromOsName("Windows 11"));
    assertEquals(OsFamily.MACOS, OsFamily.fromOsName("Mac OS X"));
    assertEquals(OsFamily.LINUX, OsFamily.fromOsName("Linux"));
    assertEquals(OsFamily.LINUX, OsFamily.fromOsName("FreeBSD"));
  }
}
