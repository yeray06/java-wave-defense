package io.github.yeray06.wavedefense.sim.data;

/**
 * A wave's code challenge (D-57): what the player's code must do, besides winning, for the wave's
 * third star. Each wave has one, in {@code waves.json}, chosen to reward the syntax that wave
 * teaches.
 *
 * @param limit the number for {@link Type#MAX_MEMORY} and {@link Type#MAX_SHOTS}; 0 otherwise
 */
public record Challenge(Type type, int limit) {

  /** What is measured. */
  public enum Type {
    /** No turret fires with no enemy in range. */
    NO_AIR_SHOTS,
    /** No exception on any turret. */
    NO_FAULTS,
    /** The player never clicks a turret to reload it. */
    NO_MANUAL_RELOADS,
    /** Every turret's code takes at most {@code limit} memory lines. */
    MAX_MEMORY,
    /** All turrets together fire at most {@code limit} bullets. */
    MAX_SHOTS,
    /** No bullet flies at an enemy that dies before it arrives. */
    NO_WASTED_SHOTS;

    /** Whether the challenge needs a number. */
    public boolean hasLimit() {
      return this == MAX_MEMORY || this == MAX_SHOTS;
    }
  }

  public Challenge {
    Check.present("challenge type", type);
    if (type.hasLimit()) {
      Check.positive("challenge limit", limit);
    } else if (limit != 0) {
      throw new IllegalArgumentException(type + " takes no limit");
    }
  }
}
