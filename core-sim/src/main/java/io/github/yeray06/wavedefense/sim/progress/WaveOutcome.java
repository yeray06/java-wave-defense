package io.github.yeray06.wavedefense.sim.progress;

/**
 * What a finished wave paid and where the player goes next.
 *
 * @param killCents money from kills (also paid on defeat)
 * @param perfectBonusCents the plan's +25 % for a wave without leaks
 * @param bossBonusCents first-kill or repeat-kill payment for the boss
 */
public record WaveOutcome(
    int wave,
    boolean won,
    boolean perfect,
    long killCents,
    long perfectBonusCents,
    long bossBonusCents,
    int nextWave) {

  public long totalCents() {
    return killCents + perfectBonusCents + bossBonusCents;
  }
}
