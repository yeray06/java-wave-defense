package io.github.yeray06.wavedefense.sim.progress;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.data.ShopConfig;
import java.util.Optional;

/**
 * Syntax and hardware purchases with the plan's rules ("Árbol de sintaxis"), paid in stars (D-57).
 */
public final class Shop {
  private final GameData data;

  public Shop(GameData data) {
    this.data = data;
  }

  /** Price in stars. */
  public int price(Feature feature) {
    return data.shop().syntax(feature).price();
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
    return affordable(progress, price(feature));
  }

  public PurchaseCheck buy(Progress progress, Feature feature) {
    PurchaseCheck check = check(progress, feature);
    if (check == PurchaseCheck.OK) {
      progress.stars -= price(feature);
      progress.features.add(feature);
    }
    return check;
  }

  /** Price in stars of the next level of {@code kind}; 0 when there is none. */
  public int price(Progress progress, HardwareKind kind) {
    ShopConfig.HardwareItem item = data.shop().hardware(kind);
    int level = progress.level(kind);
    return level < item.maxLevel() ? item.prices().get(level) : 0;
  }

  public PurchaseCheck check(Progress progress, HardwareKind kind) {
    if (!data.shop().hardware(kind).inMvp()) {
      return PurchaseCheck.NOT_IN_MVP;
    }
    if (progress.level(kind) >= data.shop().hardware(kind).maxLevel()
        || (kind == HardwareKind.EXTRA_TURRET
            && progress.turretCount() >= data.map().turretSlots().size())) {
      return PurchaseCheck.MAX_LEVEL;
    }
    if (unlockingFeature(progress, kind).isPresent()) {
      return PurchaseCheck.NEEDS_SYNTAX;
    }
    return affordable(progress, price(progress, kind));
  }

  /**
   * The syntax piece the player must buy before the next level of {@code kind}, if a cap applies:
   * the one whose cap is reached first among the pieces not owned yet. Drives the shop message
   * "Necesitas variables para seguir mejorando el daño".
   */
  public Optional<Feature> unlockingFeature(Progress progress, HardwareKind kind) {
    int level = progress.level(kind);
    Feature blocking = null;
    int lowest = Integer.MAX_VALUE;
    for (ShopConfig.LevelCap cap : data.shop().hardware(kind).caps()) {
      if (!progress.owns(cap.untilFeature())
          && level >= cap.maxLevel()
          && cap.maxLevel() < lowest) {
        blocking = cap.untilFeature();
        lowest = cap.maxLevel();
      }
    }
    return Optional.ofNullable(blocking);
  }

  public PurchaseCheck buy(Progress progress, HardwareKind kind) {
    PurchaseCheck check = check(progress, kind);
    if (check == PurchaseCheck.OK) {
      progress.stars -= price(progress, kind);
      progress.levels.merge(kind, 1, Integer::sum);
    }
    return check;
  }

  /**
   * Whether the last level of {@code kind} can be given back (D-59). With stars that are earned
   * only once, a useless purchase could leave the game without a way forward. The extra turret is
   * not given back: its code and editor tab would go with it.
   */
  public boolean canRefund(Progress progress, HardwareKind kind) {
    return kind != HardwareKind.EXTRA_TURRET && progress.level(kind) > 0;
  }

  /** Stars that giving back the last level of {@code kind} returns: what it cost. */
  public int refundValue(Progress progress, HardwareKind kind) {
    return canRefund(progress, kind)
        ? data.shop().hardware(kind).prices().get(progress.level(kind) - 1)
        : 0;
  }

  /**
   * Gives back the last level of {@code kind} for what it cost.
   *
   * @return false if there is nothing to give back
   */
  public boolean refund(Progress progress, HardwareKind kind) {
    if (!canRefund(progress, kind)) {
      return false;
    }
    progress.stars += refundValue(progress, kind);
    progress.levels.merge(kind, -1, Integer::sum);
    return true;
  }

  private static PurchaseCheck affordable(Progress progress, int price) {
    return progress.stars >= price ? PurchaseCheck.OK : PurchaseCheck.NOT_ENOUGH_STARS;
  }
}
