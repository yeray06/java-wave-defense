package io.github.yeray06.wavedefense.sim.data;

import io.github.yeray06.wavedefense.script.diag.FaultType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code data/career.json}: what is kept beyond the plan's economy (D-63): achievements, the hats
 * they give, and the limit on a turret's name. Nothing here pays stars or trophies.
 *
 * @param turretNameMaxLength the longest name a player may give a turret
 */
public record CareerConfig(
    List<String> notFromPlan, int turretNameMaxLength, List<Achievement> achievements) {

  public CareerConfig {
    notFromPlan = List.copyOf(Check.present("notFromPlan", notFromPlan));
    Check.positive("turretNameMaxLength", turretNameMaxLength);
    achievements = List.copyOf(Check.notEmpty("achievements", achievements));
    Set<String> ids = new HashSet<>();
    for (Achievement achievement : achievements) {
      if (!ids.add(achievement.id())) {
        throw new IllegalArgumentException("duplicate achievement " + achievement.id());
      }
    }
  }

  /** The achievement with this id, or {@code null}. */
  public Achievement achievement(String id) {
    for (Achievement achievement : achievements) {
      if (achievement.id().equals(id)) {
        return achievement;
      }
    }
    return null;
  }

  /** What an achievement counts. */
  public enum Kind {
    /** Different waves beaten. */
    WAVES_BEATEN,
    /** Different waves beaten without leaks. */
    PERFECT_WAVES,
    /** Code challenges met. */
    CHALLENGES,
    /** Bosses killed. */
    BOSSES,
    /** The longest streak of waves won in a row, each later than the one before. */
    BEST_STREAK,
    /** Times any turret ran {@code saludar()}, in the whole game. */
    GREETINGS,
    /** Raising the exception named in {@code fault}, once. */
    FAULT,
    /** Syntax pieces owned. */
    SYNTAX_OWNED,
    /** Waves with a record of the player's own (shots, time or memory). */
    RECORDS,
    /** Every other achievement. */
    ALL
  }

  /** A hat's form, drawn on the turret. */
  public enum HatShape {
    CAP,
    BOW,
    HELMET,
    PARTY,
    WIZARD,
    TOP_HAT,
    CROWN,
    HALO
  }

  /**
   * One achievement. Its name and description are i18n texts {@code achievement.<id>.name} and
   * {@code .description}; its hat, if any, is named the same.
   *
   * @param target how many the kind must count
   * @param fault the exception, for {@link Kind#FAULT}
   * @param hat what it lets the player put on a turret, or {@code null}
   * @param hatColor the hat's color as {@code #rrggbb}
   */
  public record Achievement(
      String id, Kind kind, int target, FaultType fault, HatShape hat, String hatColor) {
    public Achievement {
      Check.present("id", id);
      if (!id.matches("[A-Z][A-Z0-9_]*")) {
        throw new IllegalArgumentException("achievement id must be UPPER_CASE: " + id);
      }
      Check.present(id + ".kind", kind);
      if (kind != Kind.ALL) {
        Check.positive(id + ".target", target);
      }
      if ((kind == Kind.FAULT) != (fault != null)) {
        throw new IllegalArgumentException(id + ": fault goes with kind FAULT only");
      }
      if ((hat == null) != (hatColor == null)) {
        throw new IllegalArgumentException(id + ": a hat needs its color and only a hat has one");
      }
      if (hatColor != null && !hatColor.matches("#[0-9a-fA-F]{6}")) {
        throw new IllegalArgumentException(id + ": hatColor must be #rrggbb: " + hatColor);
      }
    }
  }
}
