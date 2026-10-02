package io.github.yeray06.wavedefense.platform;

/**
 * Everything the game needs from the store or OS layer. The MVP ships {@link NoOpPlatformServices};
 * a Steam implementation (steamworks4j) plugs in here after the MVP. Cloud saves use Steam
 * Auto-Cloud on the save folder, so they need no method here.
 */
public interface PlatformServices {

  /** Unlocks an achievement by its store id. Unknown ids are ignored. */
  void unlockAchievement(String achievementId);
}
