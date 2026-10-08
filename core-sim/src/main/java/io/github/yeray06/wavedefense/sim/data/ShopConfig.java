package io.github.yeray06.wavedefense.sim.data;

import io.github.yeray06.wavedefense.script.api.Feature;
import java.util.List;
import java.util.Optional;

/**
 * {@code data/shop.json}: syntax tree and hardware, with the plan's prices.
 *
 * @param exampleLines longest purchase-card example (plan: "un ejemplo de cinco líneas")
 */
public record ShopConfig(
    List<String> notFromPlan,
    int exampleLines,
    List<SyntaxItem> syntax,
    List<HardwareItem> hardware) {

  /**
   * @param requires piece that must be owned first, or {@code null} for the first one
   * @param price in stars (D-57)
   * @param arrivesAroundWave first wave of the plan's "Llega hacia la oleada" range
   * @param inMvp whether the MVP sells it (the others are shown, locked)
   * @param exampleInLibrary whether the example goes in the library (methods) rather than in a
   *     turret's script
   * @param example the purchase card's example (plan: five lines that work as they are); {@code
   *     null} for pieces the MVP does not sell
   */
  public record SyntaxItem(
      Feature feature,
      int price,
      Feature requires,
      int arrivesAroundWave,
      boolean inMvp,
      boolean exampleInLibrary,
      String example) {
    public SyntaxItem {
      Check.present("feature", feature);
      Check.positive("price", price);
      Check.positive("arrivesAroundWave", arrivesAroundWave);
    }
  }

  /**
   * @param prices stars for each level in order; their count is the item's highest level (D-57)
   * @param effectPerLevel added per level (damage: fraction of the base)
   * @param caps level limits that last until a syntax piece is bought; empty = no limit
   */
  public record HardwareItem(
      HardwareKind kind,
      List<Integer> prices,
      double effectPerLevel,
      boolean inMvp,
      List<LevelCap> caps) {
    public HardwareItem {
      Check.present("kind", kind);
      prices = List.copyOf(Check.notEmpty("prices of " + kind, prices));
      for (int price : prices) {
        Check.positive("price of " + kind, price);
      }
      Check.positive("effectPerLevel", effectPerLevel);
      caps = List.copyOf(Check.present("caps", caps));
    }

    /** Highest level that can be bought. */
    public int maxLevel() {
      return prices.size();
    }

    /** The same item with no syntax caps: for balance comparisons. */
    public HardwareItem withoutCaps() {
      return new HardwareItem(kind, prices, effectPerLevel, inMvp, List.of());
    }
  }

  /**
   * "At most {@code maxLevel} levels until {@code untilFeature} is bought": a safety net so
   * hardware cannot replace learning syntax (approved addition to the plan).
   */
  public record LevelCap(Feature untilFeature, int maxLevel) {
    public LevelCap {
      Check.present("untilFeature", untilFeature);
      Check.atLeastZero("maxLevel", maxLevel);
    }
  }

  public ShopConfig {
    Check.positive("exampleLines", exampleLines);
    notFromPlan = List.copyOf(Check.present("notFromPlan", notFromPlan));
    syntax = List.copyOf(Check.notEmpty("syntax", syntax));
    hardware = List.copyOf(Check.notEmpty("hardware", hardware));
    for (Feature feature : Feature.values()) {
      if (syntax.stream().filter(s -> s.feature() == feature).count() != 1) {
        throw new IllegalArgumentException("syntax must list " + feature + " exactly once");
      }
    }
    for (SyntaxItem item : syntax) {
      if (item.requires() == item.feature()) {
        throw new IllegalArgumentException(item.feature() + " cannot require itself");
      }
    }
    for (HardwareKind kind : HardwareKind.values()) {
      if (hardware.stream().filter(h -> h.kind() == kind).count() != 1) {
        throw new IllegalArgumentException("hardware must list " + kind + " exactly once");
      }
    }
  }

  /** The same shop with every hardware cap removed: what the double rule calls "sin tope". */
  public ShopConfig withoutCaps() {
    return new ShopConfig(
        notFromPlan,
        exampleLines,
        syntax,
        hardware.stream().map(HardwareItem::withoutCaps).toList());
  }

  public SyntaxItem syntax(Feature feature) {
    return syntax.stream().filter(s -> s.feature() == feature).findFirst().orElseThrow();
  }

  public HardwareItem hardware(HardwareKind kind) {
    return hardware.stream().filter(h -> h.kind() == kind).findFirst().orElseThrow();
  }

  /** The piece after {@code feature} in the tree, if any. */
  public Optional<SyntaxItem> next(Feature feature) {
    return syntax.stream().filter(s -> s.requires() == feature).findFirst();
  }
}
