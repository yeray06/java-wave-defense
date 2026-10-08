package io.github.yeray06.wavedefense.sim.progress;

import java.util.List;
import java.util.Set;

/**
 * What a finished wave added to the player's career (D-63), next to the stars of its {@link
 * WaveOutcome}.
 *
 * @param newMarks the marks of {@link WaveRecord} this run beat (or set for the first time)
 * @param streak the streak after this wave: 0 after a defeat
 * @param streakPoints what this wave added to the streak's score
 * @param newAchievements achievements unlocked by this wave, in data order
 */
public record CareerOutcome(
    Set<WaveRecord.Mark> newMarks, int streak, int streakPoints, List<String> newAchievements) {
  public CareerOutcome {
    newMarks = Set.copyOf(newMarks);
    newAchievements = List.copyOf(newAchievements);
  }
}
