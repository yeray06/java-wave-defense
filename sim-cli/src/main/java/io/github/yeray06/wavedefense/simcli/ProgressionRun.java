package io.github.yeray06.wavedefense.simcli;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.Progression;
import io.github.yeray06.wavedefense.sim.progress.PurchaseCheck;
import io.github.yeray06.wavedefense.sim.progress.Shop;
import io.github.yeray06.wavedefense.sim.progress.WaveOutcome;
import io.github.yeray06.wavedefense.sim.world.WaveReport;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plays a whole game from wave 1 until the last wave's boss falls, with a fixed purchase policy:
 *
 * <ol>
 *   <li>Buy the next syntax piece as soon as it is affordable.
 *   <li>While stuck behind a wall (after a defeat), buy whatever is affordable after each farmed
 *       wave: syntax first, else the extra turret, else the cheapest hardware; then retry.
 *   <li>Once all syntax is owned, spend on hardware the same way after every wave.
 * </ol>
 *
 * <p>When stuck, the player turns auto-advance back on during the farmed wave that will pay for the
 * next purchase, as a person watching the money counter would.
 */
final class ProgressionRun {
  private static final HardwareKind[] HARDWARE_BY_PREFERENCE = {
    HardwareKind.EXTRA_TURRET, HardwareKind.DAMAGE, HardwareKind.MAGAZINE, HardwareKind.HEAT_SINK
  };

  /** One wave played. */
  record Row(
      int number,
      int wave,
      boolean won,
      int leaks,
      long paidCents,
      long moneyAfterCents,
      String script,
      int turrets,
      List<String> bought,
      double seconds,
      boolean speedUp,
      int faults) {}

  /**
   * A wall: a wave lost at least once before it was first beaten.
   *
   * @param attempts tries at the wall wave, the winning one included
   * @param farmedWaves earlier waves replayed while stuck: the plan's "pasadas"
   */
  record Wall(int wave, int attempts, int farmedWaves, boolean beaten) {}

  record Result(
      List<Row> rows,
      List<Wall> walls,
      Map<Feature, Progress> beforeBuying,
      boolean bossDefeated,
      double playSeconds,
      Progress finalProgress) {}

  private final GameData data;
  private final ReferencePlayer player;
  private final PlayerConfig config;
  private final Shop shop;
  private final Progression progression;

  ProgressionRun(GameData data, ReferencePlayer player, PlayerConfig config) {
    this.data = data;
    this.player = player;
    this.config = config;
    this.shop = new Shop(data);
    this.progression = new Progression(data);
  }

  Result run() {
    Progress progress = Progress.newGame(data);
    List<Row> rows = new ArrayList<>();
    Map<Feature, Progress> beforeBuying = new EnumMap<>(Feature.class);
    Map<Integer, int[]> walls = new LinkedHashMap<>(); // wave -> {attempts, farmed, beaten}
    Integer openWall = null;
    long lastFarmPayout = 0;
    double seconds = 0;
    int lastWave = data.balance().lastWave();
    int tps = data.balance().ticksPerSecond();

    for (int number = 1; number <= config.maxWavesPlayed(); number++) {
      int wave = progress.currentWave();
      boolean stuck = openWall != null;
      if (stuck
          && !progress.autoAdvance()
          && progress.moneyCents() + lastFarmPayout >= cheapestPurchase(progress)) {
        progress.setAutoAdvance(true);
      }
      boolean speedUp = progress.speedUpAllowed(wave);
      ReferencePlayer.ChosenScript script = player.script(progress.unlocks());
      WaveReport report =
          player.play(wave, progress.unlocks(), progress.turretCount(), progress.loadout(), 1.0);
      boolean firstTimeBeaten = report.won() && !progress.beaten(wave);
      WaveOutcome outcome = progression.apply(progress, report);
      double waveSeconds = report.ticks() / (double) tps / (speedUp ? 3 : 1);
      seconds += waveSeconds;

      if (!report.won() && !progress.beaten(wave) && !walls.containsKey(wave)) {
        walls.put(wave, new int[3]);
        openWall = wave;
      }
      if (openWall != null) {
        int[] wall = walls.get(openWall);
        if (wave == openWall) {
          wall[0]++;
        } else {
          wall[1]++;
          lastFarmPayout = outcome.totalCents();
        }
        if (firstTimeBeaten && wave == openWall) {
          wall[2] = 1;
          openWall = null;
        }
      }

      List<String> bought = buy(progress, openWall != null, beforeBuying);
      rows.add(
          new Row(
              number,
              wave,
              report.won(),
              report.leaks().size(),
              outcome.totalCents(),
              progress.moneyCents(),
              script.name(),
              progress.turretCount(),
              bought,
              waveSeconds,
              speedUp,
              report.turrets().stream().mapToInt(WaveReport.TurretReport::faultCount).sum()));
      if (progress.bossKilled(lastWave)) {
        break;
      }
    }

    List<Wall> wallList = new ArrayList<>();
    walls.forEach((wave, w) -> wallList.add(new Wall(wave, w[0], w[1], w[2] == 1)));
    return new Result(
        rows, wallList, beforeBuying, progress.bossKilled(lastWave), seconds, progress);
  }

  private List<String> buy(Progress progress, boolean stuck, Map<Feature, Progress> beforeBuying) {
    List<String> bought = new ArrayList<>();
    while (true) {
      Optional<Feature> next = nextSyntax(progress);
      if (next.isPresent() && shop.check(progress, next.get()) == PurchaseCheck.OK) {
        beforeBuying.put(next.get(), progress.copy());
        shop.buy(progress, next.get());
        bought.add(next.get().name());
        if (stuck) {
          progress.setAutoAdvance(true);
          return bought;
        }
        continue;
      }
      if (next.isPresent() && !stuck) {
        return bought; // saving for the next piece
      }
      Optional<HardwareKind> hardware = affordableHardware(progress);
      if (hardware.isEmpty()) {
        return bought;
      }
      shop.buy(progress, hardware.get());
      bought.add(hardware.get().name());
      if (stuck) {
        progress.setAutoAdvance(true);
        return bought;
      }
    }
  }

  private Optional<Feature> nextSyntax(Progress progress) {
    return player.mvpFeatures().stream().filter(f -> !progress.owns(f)).findFirst();
  }

  /** The extra turret if it can be bought, else the cheapest affordable upgrade. */
  private Optional<HardwareKind> affordableHardware(Progress progress) {
    if (shop.check(progress, HardwareKind.EXTRA_TURRET) == PurchaseCheck.OK) {
      return Optional.of(HardwareKind.EXTRA_TURRET);
    }
    HardwareKind best = null;
    for (HardwareKind kind : HARDWARE_BY_PREFERENCE) {
      if (shop.check(progress, kind) == PurchaseCheck.OK
          && (best == null || shop.priceCents(progress, kind) < shop.priceCents(progress, best))) {
        best = kind;
      }
    }
    return Optional.ofNullable(best);
  }

  /** Price of the cheapest thing the player could buy next. */
  private long cheapestPurchase(Progress progress) {
    long cheapest = Long.MAX_VALUE;
    Optional<Feature> next = nextSyntax(progress);
    if (next.isPresent()) {
      cheapest = shop.priceCents(next.get());
    }
    for (HardwareKind kind : HARDWARE_BY_PREFERENCE) {
      PurchaseCheck check = shop.check(progress, kind);
      if (check == PurchaseCheck.OK || check == PurchaseCheck.NOT_ENOUGH_MONEY) {
        cheapest = Math.min(cheapest, shop.priceCents(progress, kind));
      }
    }
    return cheapest;
  }
}
