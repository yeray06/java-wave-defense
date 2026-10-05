package io.github.yeray06.wavedefense.sim.progress;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.data.ShopConfig;
import io.github.yeray06.wavedefense.sim.economy.Formulas;
import io.github.yeray06.wavedefense.sim.economy.Money;

/** Syntax and hardware purchases with the plan's prices and rules ("Árbol de sintaxis"). */
public final class Shop {
  private final GameData data;

  public Shop(GameData data) {
    this.data = data;
  }

  public long priceCents(Feature feature) {
    return Money.cents(data.shop().syntax(feature).price());
  }

  public PurchaseCheck check(Progress progress, Feature feature) {
    ShopConfig.SyntaxItem item = data.shop().syntax(feature);
    if (!item.inMvp()) {
      return PurchaseCheck.NOT_IN_MVP;
    }
    if (progress.owns(feature)) {
      return PurchaseCheck.ALREADY_OWNED;
    }
    if (item.requires() != null && !progress.owns(item.requires())) {
      return PurchaseCheck.MISSING_REQUIREMENT;
    }
    return affordable(progress, priceCents(feature));
  }

  public PurchaseCheck buy(Progress progress, Feature feature) {
    PurchaseCheck check = check(progress, feature);
    if (check == PurchaseCheck.OK) {
      progress.moneyCents -= priceCents(feature);
      progress.features.add(feature);
    }
    return check;
  }

  /** Price of the next level of {@code kind}: first cost times growth per level owned. */
  public long priceCents(Progress progress, HardwareKind kind) {
    return Formulas.hardwareCostCents(data.shop().hardware(kind), progress.level(kind));
  }

  public PurchaseCheck check(Progress progress, HardwareKind kind) {
    if (!data.shop().hardware(kind).inMvp()) {
      return PurchaseCheck.NOT_IN_MVP;
    }
    if (kind == HardwareKind.EXTRA_TURRET
        && progress.turretCount() >= data.map().turretSlots().size()) {
      return PurchaseCheck.MAX_LEVEL;
    }
    return affordable(progress, priceCents(progress, kind));
  }

  public PurchaseCheck buy(Progress progress, HardwareKind kind) {
    PurchaseCheck check = check(progress, kind);
    if (check == PurchaseCheck.OK) {
      progress.moneyCents -= priceCents(progress, kind);
      progress.levels.merge(kind, 1, Integer::sum);
    }
    return check;
  }

  private static PurchaseCheck affordable(Progress progress, long priceCents) {
    return progress.moneyCents >= priceCents ? PurchaseCheck.OK : PurchaseCheck.NOT_ENOUGH_MONEY;
  }
}
