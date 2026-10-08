package io.github.yeray06.wavedefense.game.i18n;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.diag.CompileError;
import io.github.yeray06.wavedefense.script.diag.ErrorCode;
import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.sim.data.Challenge;
import io.github.yeray06.wavedefense.sim.data.EnemyCatalog;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.progress.Goal;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.PurchaseCheck;
import io.github.yeray06.wavedefense.sim.progress.Shop;

/**
 * Turns the codes that the pure modules return (compile errors, exceptions, shop answers, enemy
 * kinds) into the player's Spanish text. Numbers inside descriptions come from the data files, so
 * the texts never repeat a balance number.
 */
public final class ScriptTexts {
  /** How the interpreter names the end of the script in "expected X and found Y" errors. */
  private static final String END_OF_CODE = "EOF";

  private final Texts texts;
  private final GameData data;

  public ScriptTexts(Texts texts, GameData data) {
    this.texts = texts;
    this.data = data;
  }

  public Texts texts() {
    return texts;
  }

  /** The game's data: names of calls, enemies and pieces come from it. */
  public GameData data() {
    return data;
  }

  /** The interpreter's names for calls and constants. */
  public io.github.yeray06.wavedefense.script.api.ScriptApi api() {
    return data.scriptApi().api();
  }

  public String feature(Feature feature) {
    return texts.get("script.feature." + feature.name());
  }

  public String fault(FaultType type) {
    return texts.get("script.fault." + type.name());
  }

  public String hardware(HardwareKind kind) {
    return texts.get("shop.hardware." + kind.name());
  }

  /** A compile error's message; features among its arguments are named in Spanish. */
  private static final java.util.Set<ErrorCode> SUGGESTS =
      java.util.EnumSet.of(
          ErrorCode.UNKNOWN_SYMBOL,
          ErrorCode.UNKNOWN_TYPE,
          ErrorCode.UNKNOWN_METHOD,
          ErrorCode.UNKNOWN_ENEMY_METHOD);

  public String error(CompileError error) {
    Object[] args = error.args().toArray();
    for (int i = 0; i < args.length; i++) {
      if (args[i] instanceof Feature feature) {
        args[i] = feature(feature);
      } else if (END_OF_CODE.equals(args[i])) {
        args[i] = texts.get("script.token.EOF");
      }
    }
    String key = "script.error." + error.code().name();
    // «¿Quisiste decir…?»: the last argument of an unknown name is the closest known one, if any.
    if (SUGGESTS.contains(error.code())
        && args.length > 0
        && args[args.length - 1] instanceof String suggestion
        && !suggestion.isEmpty()) {
      key += ".suggest";
    }
    return texts.format(key, args);
  }

  public String enemyName(EnemyKind kind) {
    return texts.get("enemy." + kind.name() + ".name");
  }

  /** What makes an enemy kind special, with its numbers from {@code enemies.json}. */
  public String enemyInfo(EnemyKind kind) {
    EnemyCatalog.EnemyType type = data.enemies().type(kind);
    Object argument =
        switch (kind) {
          case REGENERATOR -> decimal(type.regenDelaySeconds());
          case ARMORED -> decimal(data.enemies().armorDamageReduction() * 100);
          case MUTANT -> decimal(data.enemies().mutantDamageReduction() * 100);
          case SWARM -> type.groupSize();
          case BOSS -> decimal(type.armorToggleSeconds());
          case BASIC, RUNNER -> "";
        };
    return texts.format("enemy." + kind.name() + ".info", argument);
  }

  /** "1 estrella", "3 estrellas". */
  public String stars(int count) {
    return texts.format(count == 1 ? "stars.one" : "stars.many", count);
  }

  /** "1 trofeo", "2 trofeos". */
  public String trophies(int count) {
    return texts.format(count == 1 ? "trophies.one" : "trophies.many", count);
  }

  /** What a wave's code challenge asks, e.g. "52 disparos como mucho…". */
  public String challenge(Challenge challenge) {
    return texts.format("challenge." + challenge.type().name(), challenge.limit());
  }

  /** One of a wave's three stars, e.g. "Reto: ningún disparo al aire". */
  public String goal(Goal goal, Challenge challenge) {
    return goal == Goal.CHALLENGE
        ? texts.format("goal.CHALLENGE", challenge(challenge))
        : texts.get("goal." + goal.name());
  }

  /** A goal with its mark: "✓ …" once earned, "○ …" while missing. */
  public String goalLine(Goal goal, Challenge challenge, boolean met) {
    return texts.format(met ? "goal.met" : "goal.missing", goal(goal, challenge));
  }

  /** Why a syntax piece cannot be bought now. */
  public String check(PurchaseCheck check, Shop shop, Feature feature) {
    return switch (check) {
      case OK -> "";
      case NOT_ENOUGH_STARS ->
          texts.format("shop.check.NOT_ENOUGH_STARS", stars(shop.price(feature)));
      case MISSING_REQUIREMENT ->
          texts.format(
              "shop.check.MISSING_REQUIREMENT", feature(data.shop().syntax(feature).requires()));
      default -> texts.get("shop.check." + check.name());
    };
  }

  /** Why a hardware item cannot be bought now. */
  public String check(PurchaseCheck check, Shop shop, Progress progress, HardwareKind kind) {
    return switch (check) {
      case OK -> "";
      case NOT_ENOUGH_STARS ->
          texts.format("shop.check.NOT_ENOUGH_STARS", stars(shop.price(progress, kind)));
      case NEEDS_SYNTAX ->
          texts.format(
              "shop.check.NEEDS_SYNTAX",
              feature(shop.unlockingFeature(progress, kind).orElseThrow()),
              hardware(kind));
      default -> texts.get("shop.check." + check.name());
    };
  }

  /** "3" for 3.0, "0,5" for 0.5. */
  private static String decimal(double value) {
    if (value == Math.rint(value)) {
      return String.valueOf((long) value);
    }
    return String.valueOf(value).replace('.', ',');
  }
}
