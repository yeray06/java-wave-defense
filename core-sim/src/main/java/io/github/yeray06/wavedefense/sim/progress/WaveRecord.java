package io.github.yeray06.wavedefense.sim.progress;

/**
 * The player's best marks on one wave, each from any won run (D-63). Lower is better in all three;
 * they are only for showing off and never pay anything.
 *
 * @param ticks the fastest win, in simulation ticks
 * @param shots the fewest shots fired by all turrets together
 * @param memory the fewest memory lines of the largest turret script
 */
public record WaveRecord(int ticks, int shots, int memory) {
  public WaveRecord {
    if (ticks < 0 || shots < 0 || memory < 0) {
      throw new IllegalArgumentException("negative record");
    }
  }

  /** Which of the three marks a run can beat. */
  public enum Mark {
    TICKS,
    SHOTS,
    MEMORY
  }

  /** The value of a mark. */
  public int value(Mark mark) {
    return switch (mark) {
      case TICKS -> ticks;
      case SHOTS -> shots;
      case MEMORY -> memory;
    };
  }
}
