package io.github.yeray06.wavedefense.simcli;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.data.ShopConfig;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.PurchaseCheck;
import io.github.yeray06.wavedefense.sim.progress.Shop;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The plan's "regla del doble" (D-17): a syntax piece, well used, must perform at least twice as
 * well as spending its price on hardware.
 *
 * <p>Performance is {@code k}: the largest enemy-health multiplier with which the reference player
 * still clears the wave where the piece "arrives" (shop.json, first wave of the plan's range)
 * without leaks. Merely winning is no measure there: with 20 core lives, waves of fewer than 20
 * enemies (1–6) cannot be lost at all. Starting from the player's state just before buying the
 * piece, it compares (a) the piece and its script against (b) no piece and the best hardware the
 * same stars buy.
 */
final class DoubleRule {
  private static final HardwareKind[] HARDWARE = {
    HardwareKind.EXTRA_TURRET,
    HardwareKind.DAMAGE,
    HardwareKind.MAGAZINE,
    HardwareKind.HEAT_SINK,
    HardwareKind.PROCESSOR,
    HardwareKind.MEMORY
  };

  record Entry(
      Feature feature,
      int wave,
      int price,
      String scriptWith,
      double kWith,
      String scriptWithout,
      String bestHardware,
      double kWithout,
      double kNeither,
      boolean evaluated) {

    double ratio() {
      return kWithout == 0 ? Double.POSITIVE_INFINITY : kWith / kWithout;
    }

    boolean passes() {
      return evaluated && ratio() >= 2;
    }
  }

  private final GameData data;
  private final ReferencePlayer player;
  private final PlayerConfig.Search search;
  private final Shop shop;

  DoubleRule(GameData data, ReferencePlayer player, PlayerConfig config) {
    this.data = data;
    this.player = player;
    this.search = config.healthScaleSearch();
    this.shop = new Shop(data);
  }

  List<Entry> evaluate(Map<Feature, Progress> beforeBuying) {
    List<Entry> entries = new ArrayList<>();
    for (ShopConfig.SyntaxItem item : data.shop().syntax()) {
      if (!item.inMvp()) {
        continue;
      }
      Progress base = beforeBuying.get(item.feature());
      int price = shop.price(item.feature());
      if (base == null) {
        entries.add(
            new Entry(item.feature(), item.arrivesAroundWave(), price, "", 0, "", "", 0, 0, false));
        continue;
      }
      int wave = item.arrivesAroundWave();
      int reached = Math.max(base.highestWaveReached(), wave);

      Set<Feature> with = EnumSet.noneOf(Feature.class);
      with.addAll(base.features());
      with.add(item.feature());
      Unlocks unlocksWith = new Unlocks(with, reached);
      Best withPiece = bestK(wave, unlocksWith, base);

      Unlocks unlocksWithout = new Unlocks(base.features(), reached);
      Best neither = bestK(wave, unlocksWithout, base);
      Best withoutPiece = null;
      String bestHardware = "";
      for (Progress option : hardwareOptions(base.copyWithStars(price))) {
        Best best = bestK(wave, unlocksWithout, option);
        if (withoutPiece == null || best.k() > withoutPiece.k()) {
          withoutPiece = best;
          bestHardware = describeHardware(base, option);
        }
      }
      entries.add(
          new Entry(
              item.feature(),
              wave,
              price,
              withPiece.script(),
              withPiece.k(),
              withoutPiece.script(),
              bestHardware,
              withoutPiece.k(),
              neither.k(),
              true));
    }
    return entries;
  }

  /** Every way to spend the stars {@code budget} holds on MVP hardware. */
  private List<Progress> hardwareOptions(Progress budget) {
    List<Progress> options = new ArrayList<>();
    collect(budget, 0, options);
    return options;
  }

  private void collect(Progress state, int fromKind, List<Progress> out) {
    out.add(state);
    for (int i = fromKind; i < HARDWARE.length; i++) {
      if (shop.check(state, HARDWARE[i]) == PurchaseCheck.OK) {
        Progress next = state.copy();
        shop.buy(next, HARDWARE[i]);
        collect(next, i, out);
      }
    }
  }

  private String describeHardware(Progress base, Progress option) {
    List<String> parts = new ArrayList<>();
    for (HardwareKind kind : HARDWARE) {
      int added = option.level(kind) - base.level(kind);
      if (added > 0) {
        parts.add(kind.name() + " +" + added);
      }
    }
    return parts.isEmpty() ? "nada" : String.join(", ", parts);
  }

  /** The best k among the script variants these unlocks allow, and which variant gave it. */
  private record Best(double k, String script) {}

  private Best bestK(int wave, Unlocks unlocks, Progress state) {
    Best best = null;
    for (ReferencePlayer.ChosenScript script : player.variants(unlocks, state.memoryLines(data))) {
      double k = bestK(wave, script, state);
      if (best == null || k > best.k()) {
        best = new Best(k, script.name());
      }
    }
    return best;
  }

  /** Largest health multiplier that still gives a perfect wave, by bisection in log space. */
  private double bestK(int wave, ReferencePlayer.ChosenScript script, Progress state) {
    if (!wins(wave, script, state, search.min())) {
      return 0;
    }
    if (wins(wave, script, state, search.max())) {
      return search.max();
    }
    double lo = StrictMath.log(search.min());
    double hi = StrictMath.log(search.max());
    for (int i = 0; i < search.iterations(); i++) {
      double mid = (lo + hi) / 2;
      if (wins(wave, script, state, StrictMath.exp(mid))) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return StrictMath.exp(lo);
  }

  private boolean wins(int wave, ReferencePlayer.ChosenScript script, Progress state, double k) {
    return player.play(wave, script, state.turretCount(), state.loadout(), k).perfect();
  }
}
