package io.github.yeray06.wavedefense.simcli;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.sim.data.Challenge;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.progress.ChallengeJudge;
import io.github.yeray06.wavedefense.sim.progress.Goal;
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
 * Plays a whole game from wave 1 until the last wave's boss falls, with stars (D-57) and a fixed
 * policy:
 *
 * <ol>
 *   <li>Play the current wave with the best script. Won but the code challenge missed: if some
 *       script the player can write wins and meets it, play the wave again with that one.
 *   <li>Buy the next syntax piece as soon as it is affordable; with all of it, spend on hardware.
 *   <li>Stuck behind a wall (a defeat): buy syntax if affordable, else the extra turret, else the
 *       cheapest hardware, and retry. With nothing affordable, replay the first earlier wave that
 *       can still give a star (no leaks, or its challenge). With no such wave, the game is locked.
 * </ol>
 *
 * <p>The player knows in advance whether a script meets a challenge or a wave goes without leaks:
 * an upper bound of a person who reads the challenge and tries. Replaying pays nothing, so there is
 * no farming.
 */
final class ProgressionRun {
  private static final HardwareKind[] HARDWARE_BY_PREFERENCE = {
    HardwareKind.EXTRA_TURRET, HardwareKind.DAMAGE, HardwareKind.MAGAZINE, HardwareKind.HEAT_SINK
  };

  /** Why a wave was played. */
  enum Purpose {
    ADVANCE,
    CHALLENGE,
    WALL,
    REVISIT
  }

  /** One wave played. */
  record Row(
      int number,
      int wave,
      Purpose purpose,
      boolean won,
      int leaks,
      List<Goal> earned,
      int starsAfter,
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
   * @param passes earlier waves replayed while stuck
   */
  record Wall(int wave, int attempts, int passes, boolean beaten) {}

  record Result(
      List<Row> rows,
      List<Wall> walls,
      Map<Feature, Progress> beforeBuying,
      boolean bossDefeated,
      Integer lockedAt,
      double playSeconds,
      Progress finalProgress,
      Map<Integer, String> challengeAtTheEnd,
      int starsSpentBeforeTheBoss) {}

  private final GameData data;
  private final ReferencePlayer player;
  private final PlayerConfig config;
  private final Shop shop;
  private final Progression progression;
  private final boolean triesChallenges;

  ProgressionRun(GameData data, ReferencePlayer player, PlayerConfig config) {
    this(data, player, config, true);
  }

  /**
   * @param triesChallenges false for a player who ignores the code challenges: how far the game
   *     goes on clearing waves alone
   */
  ProgressionRun(
      GameData data, ReferencePlayer player, PlayerConfig config, boolean triesChallenges) {
    this.data = data;
    this.player = player;
    this.config = config;
    this.triesChallenges = triesChallenges;
    this.shop = new Shop(data);
    this.progression = new Progression(data);
  }

  Result run() {
    Progress progress = Progress.newGame(data);
    List<Row> rows = new ArrayList<>();
    Map<Feature, Progress> beforeBuying = new EnumMap<>(Feature.class);
    Map<Integer, int[]> walls = new LinkedHashMap<>(); // wave -> {attempts, passes, beaten}
    Integer wall = null;
    Integer locked = null;
    double seconds = 0;
    int lastWave = data.balance().lastWave();
    int spentBeforeTheBoss = 0;

    while (rows.size() < config.maxWavesPlayed() && !progress.bossKilled(lastWave)) {
      progress.setAutoAdvance(true); // the player switches it back on after a defeat
      int wave = progress.currentWave();
      Purpose purpose = wall == null ? Purpose.ADVANCE : Purpose.WALL;
      boolean firstTime = !progress.beaten(wave);
      ReferencePlayer.ChosenScript script = player.script(progress.unlocks());
      Row row = play(rows.size() + 1, progress, wave, script, purpose);
      seconds += row.seconds();

      if (!row.won() && firstTime) {
        walls.computeIfAbsent(wave, w -> new int[3]);
        wall = wave;
      }
      if (wall != null && wave == wall) {
        walls.get(wall)[0]++;
      }
      if (row.won() && wall != null && wave == wall) {
        walls.get(wall)[2] = 1;
        wall = null;
      }
      rows.add(withPurchases(row, progress, wall, beforeBuying));

      if (row.won() && !progress.met(wave, Goal.CHALLENGE)) {
        Optional<ReferencePlayer.ChosenScript> meets = challengeScript(progress, wave);
        if (meets.isPresent()) {
          int next = progress.currentWave();
          progress.goTo(wave);
          Row retry = play(rows.size() + 1, progress, wave, meets.get(), Purpose.CHALLENGE);
          seconds += retry.seconds();
          progress.goTo(next);
          rows.add(withPurchases(retry, progress, null, beforeBuying));
        }
      }
      if (progress.bossKilled(lastWave)) {
        break;
      }
      spentBeforeTheBoss = progress.starsEarned() - progress.stars();
      if (wall == null) {
        continue;
      }

      // Stuck: retry the wall after any purchase; otherwise go back for a missing star.
      if (!rows.get(rows.size() - 1).bought().isEmpty()) {
        progress.goTo(wall);
        continue;
      }
      Optional<Revisit> revisit = revisit(progress, wall);
      if (revisit.isEmpty()) {
        locked = wall;
        break;
      }
      progress.goTo(revisit.get().wave());
      Row pass =
          play(
              rows.size() + 1,
              progress,
              revisit.get().wave(),
              revisit.get().script(),
              Purpose.REVISIT);
      seconds += pass.seconds();
      walls.get(wall)[1]++;
      rows.add(withPurchases(pass, progress, wall, beforeBuying));
      progress.goTo(wall);
    }

    List<Wall> wallList = new ArrayList<>();
    walls.forEach((wave, w) -> wallList.add(new Wall(wave, w[0], w[1], w[2] == 1)));
    return new Result(
        rows,
        wallList,
        beforeBuying,
        progress.bossKilled(lastWave),
        locked,
        seconds,
        progress,
        challengeAtTheEnd(progress),
        spentBeforeTheBoss);
  }

  /** Per wave, the first script that meets its challenge with what was owned at the end. */
  private Map<Integer, String> challengeAtTheEnd(Progress progress) {
    Map<Integer, String> scripts = new LinkedHashMap<>();
    for (int wave = 1; wave <= data.balance().lastWave(); wave++) {
      scripts.put(
          wave, challengeScript(progress, wave).map(ReferencePlayer.ChosenScript::name).orElse(""));
    }
    return scripts;
  }

  private Row play(
      int number,
      Progress progress,
      int wave,
      ReferencePlayer.ChosenScript script,
      Purpose purpose) {
    boolean speedUp = progress.speedUpAllowed(wave);
    WaveReport report = player.play(wave, script, progress.turretCount(), progress.loadout(), 1.0);
    WaveOutcome outcome = progression.apply(progress, report, script.memoryUsed());
    double waveSeconds =
        report.ticks() / (double) data.balance().ticksPerSecond() / (speedUp ? 3 : 1);
    return new Row(
        number,
        wave,
        purpose,
        report.won(),
        report.leaks().size(),
        outcome.newGoals(),
        progress.stars(),
        script.name(),
        progress.turretCount(),
        List.of(),
        waveSeconds,
        speedUp,
        report.turrets().stream().mapToInt(WaveReport.TurretReport::faultCount).sum());
  }

  private Row withPurchases(
      Row row, Progress progress, Integer wall, Map<Feature, Progress> beforeBuying) {
    List<String> bought = buy(progress, wall, beforeBuying);
    if (bought.isEmpty()) {
      return row;
    }
    return new Row(
        row.number(),
        row.wave(),
        row.purpose(),
        row.won(),
        row.leaks(),
        row.earned(),
        progress.stars(),
        row.script(),
        progress.turretCount(),
        bought,
        row.seconds(),
        row.speedUp(),
        row.faults());
  }

  /** The first script, strongest first, that wins the wave and meets its challenge. */
  private Optional<ReferencePlayer.ChosenScript> challengeScript(Progress progress, int wave) {
    if (!triesChallenges) {
      return Optional.empty();
    }
    Challenge challenge = data.waves().wave(wave).challenge();
    for (ReferencePlayer.ChosenScript script : player.allScripts(progress.unlocks())) {
      WaveReport report =
          player.play(wave, script, progress.turretCount(), progress.loadout(), 1.0);
      if (ChallengeJudge.met(challenge, report, script.memoryUsed())) {
        return Optional.of(script);
      }
    }
    return Optional.empty();
  }

  private record Revisit(int wave, ReferencePlayer.ChosenScript script) {}

  /** The first earlier wave that gives a star now: without leaks, or its challenge. */
  private Optional<Revisit> revisit(Progress progress, int wall) {
    for (int wave = 1; wave < wall; wave++) {
      if (!progress.met(wave, Goal.PERFECT)) {
        ReferencePlayer.ChosenScript best = player.script(progress.unlocks());
        if (player.play(wave, best, progress.turretCount(), progress.loadout(), 1.0).perfect()) {
          return Optional.of(new Revisit(wave, best));
        }
      }
      if (!progress.met(wave, Goal.CHALLENGE)) {
        Optional<ReferencePlayer.ChosenScript> meets = challengeScript(progress, wave);
        if (meets.isPresent()) {
          return Optional.of(new Revisit(wave, meets.get()));
        }
      }
    }
    return Optional.empty();
  }

  private List<String> buy(Progress progress, Integer wall, Map<Feature, Progress> beforeBuying) {
    boolean stuck = wall != null;
    List<String> bought = new ArrayList<>();
    while (true) {
      Optional<Feature> next = nextSyntax(progress);
      if (next.isPresent() && shop.check(progress, next.get()) == PurchaseCheck.OK) {
        beforeBuying.put(next.get(), progress.copy());
        shop.buy(progress, next.get());
        bought.add(next.get().name());
        continue;
      }
      if (next.isPresent() && !stuck) {
        return bought; // saving for the next piece
      }
      Optional<HardwareKind> hardware =
          stuck
              ? mostUsefulHardware(progress, wall, true)
              : mostUsefulHardware(progress, progress.currentWave(), false);
      if (hardware.isEmpty()) {
        return bought;
      }
      shop.buy(progress, hardware.get());
      bought.add(hardware.get().name());
      if (stuck) {
        return bought; // one thing at a time, then try the wall again
      }
    }
  }

  private Optional<Feature> nextSyntax(Progress progress) {
    return player.mvpFeatures().stream().filter(f -> !progress.owns(f)).findFirst();
  }

  /**
   * Stuck: the affordable upgrade that most raises the health multiplier the player still beats the
   * wall with, per star; ties go to {@link #HARDWARE_BY_PREFERENCE}. A player who reads the defeat
   * and buys what was missing, never something useless.
   */
  private Optional<HardwareKind> mostUsefulHardware(
      Progress progress, int wave, boolean evenIfUseless) {
    double now = strength(wave, progress);
    HardwareKind best = null;
    double bestGain = 0;
    for (HardwareKind kind : HARDWARE_BY_PREFERENCE) {
      if (shop.check(progress, kind) != PurchaseCheck.OK) {
        continue;
      }
      Progress option = progress.copy();
      int price = shop.price(progress, kind);
      shop.buy(option, kind);
      double gain = (strength(wave, option) - now) / price;
      if (best == null || gain > bestGain) {
        best = kind;
        bestGain = gain;
      }
    }
    return bestGain > 0 || evenIfUseless ? Optional.ofNullable(best) : Optional.empty();
  }

  /** Largest enemy-health multiplier the campaign script still wins the wave with. */
  private double strength(int wave, Progress progress) {
    ReferencePlayer.ChosenScript script = player.script(progress.unlocks());
    PlayerConfig.Search search = config.healthScaleSearch();
    double lo = StrictMath.log(search.min());
    double hi = StrictMath.log(search.max());
    if (!wins(wave, script, progress, search.min())) {
      return 0;
    }
    for (int i = 0; i < search.iterations(); i++) {
      double mid = (lo + hi) / 2;
      if (wins(wave, script, progress, StrictMath.exp(mid))) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return StrictMath.exp(lo);
  }

  private boolean wins(
      int wave, ReferencePlayer.ChosenScript script, Progress progress, double healthScale) {
    return player.play(wave, script, progress.turretCount(), progress.loadout(), healthScale).won();
  }
}
