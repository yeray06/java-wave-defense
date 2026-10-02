package io.github.yeray06.wavedefense.lwjgl3;

import io.github.yeray06.wavedefense.platform.OsFamily;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * macOS requires GLFW to run on the process's first thread, which the JVM only grants with {@code
 * -XstartOnFirstThread}. If that flag is missing, this relaunches the JVM with it, so players and
 * IntelliJ run configurations never need to add it by hand. Same approach as the helper generated
 * by gdx-liftoff.
 */
final class StartupHelper {
  private static final String FIRST_THREAD_FLAG = "-XstartOnFirstThread";
  private static final String RELAUNCHED_PROPERTY = "wavedefense.relaunched";

  private StartupHelper() {}

  /**
   * Returns {@code true} if a child JVM was started and has finished, in which case the caller must
   * return without starting the game.
   */
  static boolean relaunchedOnMainThread() {
    if (OsFamily.current() != OsFamily.MACOS) {
      return false;
    }
    // Never relaunch twice, even if the variable below is missing for some reason.
    if (Boolean.getBoolean(RELAUNCHED_PROPERTY)) {
      return false;
    }
    long pid = ProcessHandle.current().pid();
    // The JVM sets this variable to "1" when it was started with -XstartOnFirstThread.
    if ("1".equals(System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + pid))) {
      return false;
    }

    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    command.add(FIRST_THREAD_FLAG);
    command.add("-D" + RELAUNCHED_PROPERTY + "=true");
    command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(Lwjgl3Launcher.class.getName());

    try {
      Process child = new ProcessBuilder(command).inheritIO().start();
      child.waitFor();
    } catch (IOException e) {
      // Could not relaunch: fall back to starting here; GLFW will report the problem itself.
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return true;
  }
}
