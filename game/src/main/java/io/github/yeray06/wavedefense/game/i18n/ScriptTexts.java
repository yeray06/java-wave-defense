package io.github.yeray06.wavedefense.game.i18n;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.diag.CompileError;
import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.sim.data.EnemyCatalog;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.economy.Money;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.PurchaseCheck;
import io.github.yeray06.wavedefense.sim.progress.Shop;

/**
 * Turns the codes that the pure modules return (compile errors, exceptions, shop answers, enemy
 * kinds) into the player's Spanish text. Numbers inside descriptions come from the data files, so
 * the texts never repeat a balance number.
 */
public final class ScriptTexts {
  private final Texts texts;
  private final GameData data;

  public ScriptTexts(Texts texts, GameData data) {
    this.texts = texts;
    this.data = data;
  }

  public Texts texts() {
    return texts;
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
  public String error(CompileError error) {
    Object[] args = error.args().toArray();
    for (int i = 0; i < args.length; i++) {
      if (args[i] instanceof Feature feature) {
        args[i] = feature(feature);
      }
    }
    return texts.format("script.error." + error.code().name(), args);
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
          case SWARM -> type.groupSize();
          case BOSS -> decimal(type.armorToggleSeconds());
          case BASIC -> "";
        };
    return texts.format("enemy." + kind.name() + ".info", argument);
  }

  /** Why a syntax piece cannot be bought now. */
  public String check(PurchaseCheck check, Shop shop, Feature feature) {
    return switch (check) {
      case OK -> "";
      case NOT_ENOUGH_MONEY ->
          texts.format("shop.check.NOT_ENOUGH_MONEY", Money.wholeUnits(shop.priceCents(feature)));
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
      case NOT_ENOUGH_MONEY ->
          texts.format(
              "shop.check.NOT_ENOUGH_MONEY", Money.wholeUnits(shop.priceCents(progress, kind)));
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
