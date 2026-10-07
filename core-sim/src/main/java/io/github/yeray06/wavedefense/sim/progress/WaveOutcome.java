package io.github.yeray06.wavedefense.sim.progress;

import java.util.List;

/**
 * What a finished wave earned and where the player goes next (D-57).
 *
 * @param challengeMet whether this run met the wave's code challenge, earned before or not
 * @param challengeValue what the challenge counted in this run ({@link ChallengeJudge#measure})
 * @param newGoals goals met for the first time: one star each
 * @param trophies boss trophies earned: the first kill of a boss only
 */
public record WaveOutcome(
    int wave,
    boolean won,
    boolean perfect,
    boolean challengeMet,
    int challengeValue,
    List<Goal> newGoals,
    int trophies,
    int nextWave) {

  public WaveOutcome {
    newGoals = List.copyOf(newGoals);
  }

  /** Stars earned by this wave. */
  public int stars() {
    return newGoals.size();
  }
}
