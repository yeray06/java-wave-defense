package io.github.yeray06.wavedefense.platform;

/** Platform services for builds without a store integration: every call does nothing. */
public final class NoOpPlatformServices implements PlatformServices {

  @Override
  public void unlockAchievement(String achievementId) {
    // No store attached: achievements are not tracked.
  }
}
