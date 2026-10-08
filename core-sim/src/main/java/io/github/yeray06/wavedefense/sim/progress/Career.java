package io.github.yeray06.wavedefense.sim.progress;

import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.sim.data.CareerConfig;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.world.WaveReport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Everything the player keeps beyond the plan's economy (D-63): records per wave, the streak and
 * its score, achievements, the hats they give and the names of the turrets. None of it buys
 * anything; it is there to give the player goals of their own once a wave's stars are earned.
 *
 * <p>The streak counts waves won in a row, each one later than the last: replaying a wave starts a
 * new streak, and a defeat ends it. Each wave adds its number times the streak's length, so a long
 * streak is worth much more than several short ones.
 */
public final class Career {
  private final CareerConfig config;
  private final int slots;
  private final TreeMap<Integer, WaveRecord> records = new TreeMap<>();
  private int streak;
  private int streakLastWave;
  private long streakScore;
  private int bestStreak;
  private long bestStreakScore;
  private long greetings;
  private final EnumSet<FaultType> faultsSeen = EnumSet.noneOf(FaultType.class);
  private final LinkedHashSet<String> achievements = new LinkedHashSet<>();
  private final List<String> names;
  private final List<String> hats;
  private boolean expert;

  private Career(GameData data) {
    this.config = data.career();
    this.slots = data.map().turretSlots().size();
    this.names = new ArrayList<>(Collections.nCopies(slots, ""));
    this.hats = new ArrayList<>(Collections.nCopies(slots, ""));
  }

  /** A new game's career; {@code expert} for a game that starts with all syntax (D-64). */
  public static Career newGame(GameData data, boolean expert) {
    Career career = new Career(data);
    career.expert = expert;
    return career;
  }

  /**
   * Everything a saved game keeps of the career, as plain values.
   *
   * @param records per wave
   * @param streak the streak going on
   * @param streakLastWave the last wave won in it, 0 without a streak
   * @param names each turret slot's name, empty for the default one
   * @param hats each turret slot's hat: an achievement's id, or empty
   * @param expert whether the game started with all syntax (D-64)
   */
  public record Snapshot(
      Map<Integer, WaveRecord> records,
      int streak,
      int streakLastWave,
      long streakScore,
      int bestStreak,
      long bestStreakScore,
      long greetings,
      Set<FaultType> faultsSeen,
      List<String> achievements,
      List<String> names,
      List<String> hats,
      boolean expert) {
    public Snapshot {
      records = Map.copyOf(records);
      faultsSeen = Set.copyOf(faultsSeen);
      achievements = List.copyOf(achievements);
      names = List.copyOf(names);
      hats = List.copyOf(hats);
    }

    /** What a game saved before the career existed starts with. */
    public static Snapshot empty() {
      return new Snapshot(
          Map.of(), 0, 0, 0, 0, 0, 0, Set.of(), List.of(), List.of(), List.of(), false);
    }
  }

  public Snapshot snapshot() {
    return new Snapshot(
        records,
        streak,
        streakLastWave,
        streakScore,
        bestStreak,
        bestStreakScore,
        greetings,
        faultsSeen,
        List.copyOf(achievements),
        names,
        hats,
        expert);
  }

  /**
   * A career from a saved game, checked against the data. Achievements the data no longer has are
   * dropped, and so are hats that are not unlocked; names are cleaned like the player's.
   *
   * @throws IllegalArgumentException naming what does not fit
   */
  public static Career restore(GameData data, Snapshot saved) {
    Career career = new Career(data);
    int lastWave = data.balance().lastWave();
    for (var entry : saved.records().entrySet()) {
      require(entry.getKey() >= 1 && entry.getKey() <= lastWave, "record for wave out of range");
      career.records.put(entry.getKey(), entry.getValue());
    }
    require(saved.streak() >= 0 && saved.bestStreak() >= saved.streak(), "bad streak");
    require(saved.streakLastWave() >= 0 && saved.streakLastWave() <= lastWave, "bad streak wave");
    require(
        saved.streakScore() >= 0 && saved.bestStreakScore() >= saved.streakScore(),
        "bad streak score");
    require(saved.greetings() >= 0, "negative greetings");
    career.streak = saved.streak();
    career.streakLastWave = saved.streakLastWave();
    career.streakScore = saved.streakScore();
    career.bestStreak = saved.bestStreak();
    career.bestStreakScore = saved.bestStreakScore();
    career.greetings = saved.greetings();
    career.faultsSeen.addAll(saved.faultsSeen());
    for (String id : saved.achievements()) {
      if (data.career().achievement(id) != null) {
        career.achievements.add(id);
      }
    }
    for (int i = 0; i < career.slots; i++) {
      if (i < saved.names().size()) {
        career.names.set(i, career.cleanName(saved.names().get(i)));
      }
      if (i < saved.hats().size() && career.canWear(saved.hats().get(i))) {
        career.hats.set(i, saved.hats().get(i));
      }
    }
    career.expert = saved.expert();
    return career;
  }

  private static void require(boolean condition, String problem) {
    if (!condition) {
      throw new IllegalArgumentException(problem);
    }
  }

  /**
   * Applies a finished wave: its records, the streak, what the turrets did and the achievements
   * this unlocks.
   *
   * @param memoryUsed the most memory lines any turret's code took
   */
  public CareerOutcome apply(Progress progress, WaveReport report, int memoryUsed) {
    Set<WaveRecord.Mark> marks = EnumSet.noneOf(WaveRecord.Mark.class);
    int points = 0;
    if (report.won()) {
      WaveRecord run = new WaveRecord(report.ticks(), report.totalShots(), memoryUsed);
      WaveRecord old = records.get(report.wave());
      int ticks = run.ticks();
      int shots = run.shots();
      int memory = run.memory();
      for (WaveRecord.Mark mark : WaveRecord.Mark.values()) {
        if (old == null || run.value(mark) < old.value(mark)) {
          marks.add(mark);
        }
      }
      if (old != null) {
        ticks = Math.min(ticks, old.ticks());
        shots = Math.min(shots, old.shots());
        memory = Math.min(memory, old.memory());
      }
      records.put(report.wave(), new WaveRecord(ticks, shots, memory));

      if (streak > 0 && report.wave() > streakLastWave) {
        streak++;
      } else {
        streak = 1;
        streakScore = 0;
      }
      streakLastWave = report.wave();
      points = report.wave() * streak;
      streakScore += points;
      bestStreak = Math.max(bestStreak, streak);
      bestStreakScore = Math.max(bestStreakScore, streakScore);
    } else {
      streak = 0;
      streakLastWave = 0;
      streakScore = 0;
    }
    for (WaveReport.TurretReport turret : report.turrets()) {
      greetings += turret.greetings();
      for (WaveReport.FaultCount fault : turret.faults()) {
        faultsSeen.add(fault.type());
      }
    }
    return new CareerOutcome(marks, streak, points, unlock(progress));
  }

  /**
   * Unlocks every achievement the game has reached and not unlocked yet; also after a purchase.
   *
   * @return their ids, in data order
   */
  public List<String> unlock(Progress progress) {
    List<String> unlocked = new ArrayList<>();
    for (CareerConfig.Achievement a : config.achievements()) {
      if (a.kind() != CareerConfig.Kind.ALL
          && !achievements.contains(a.id())
          && reached(a, progress)) {
        achievements.add(a.id());
        unlocked.add(a.id());
      }
    }
    for (CareerConfig.Achievement a : config.achievements()) {
      if (a.kind() == CareerConfig.Kind.ALL
          && !achievements.contains(a.id())
          && reached(a, progress)) {
        achievements.add(a.id());
        unlocked.add(a.id());
      }
    }
    return unlocked;
  }

  /** How far the game is towards an achievement, in its kind's unit. */
  public long count(CareerConfig.Achievement a, Progress progress) {
    return switch (a.kind()) {
      case WAVES_BEATEN -> progress.beatenWaves.cardinality();
      case PERFECT_WAVES -> progress.perfectWaves.cardinality();
      case CHALLENGES -> progress.challengeWaves.cardinality();
      case BOSSES -> progress.bossesKilled.cardinality();
      case BEST_STREAK -> bestStreak;
      case GREETINGS -> greetings;
      case FAULT -> faultsSeen.contains(a.fault()) ? 1 : 0;
      case SYNTAX_OWNED -> progress.features.size();
      case RECORDS -> records.size();
      case ALL -> othersUnlocked();
    };
  }

  /** What an achievement's count must reach. */
  public long target(CareerConfig.Achievement a) {
    return a.kind() == CareerConfig.Kind.ALL ? config.achievements().size() - 1 : a.target();
  }

  private boolean reached(CareerConfig.Achievement a, Progress progress) {
    return count(a, progress) >= target(a);
  }

  private long othersUnlocked() {
    long count = 0;
    for (CareerConfig.Achievement a : config.achievements()) {
      if (a.kind() != CareerConfig.Kind.ALL && achievements.contains(a.id())) {
        count++;
      }
    }
    return count;
  }

  public boolean unlocked(String achievement) {
    return achievements.contains(achievement);
  }

  /** Unlocked achievements, in the order they were unlocked. */
  public List<String> achievements() {
    return List.copyOf(achievements);
  }

  /** The player's best marks on a wave, or {@code null} if never won. */
  public WaveRecord record(int wave) {
    return records.get(wave);
  }

  public int streak() {
    return streak;
  }

  public long streakScore() {
    return streakScore;
  }

  public int bestStreak() {
    return bestStreak;
  }

  public long bestStreakScore() {
    return bestStreakScore;
  }

  public long greetings() {
    return greetings;
  }

  public boolean expert() {
    return expert;
  }

  // ------------------------------------------------------------------ turrets

  /** A turret slot's name, or empty for the default one ("Torreta 1"…). */
  public String name(int slot) {
    return names.get(slot);
  }

  /**
   * Names a turret slot. Letters, digits, spaces and {@code - _ .} only, trimmed and cut to the
   * data's length: the game's font draws these and nothing else.
   *
   * @return the name kept
   */
  public String setName(int slot, String name) {
    String clean = cleanName(name);
    names.set(slot, clean);
    return clean;
  }

  private String cleanName(String name) {
    StringBuilder clean = new StringBuilder();
    for (int i = 0; i < name.length() && clean.length() < config.turretNameMaxLength(); i++) {
      char c = name.charAt(i);
      if (Character.isLetterOrDigit(c) && c < 0x250
          || c == ' '
          || c == '-'
          || c == '_'
          || c == '.') {
        clean.append(c);
      }
    }
    return clean.toString().strip();
  }

  /** A turret slot's hat: an achievement's id, or empty. */
  public String hat(int slot) {
    return hats.get(slot);
  }

  /**
   * Puts a hat on a turret slot, or takes it off with an empty id.
   *
   * @return false if the hat is not unlocked
   */
  public boolean setHat(int slot, String hat) {
    if (!hat.isEmpty() && !canWear(hat)) {
      return false;
    }
    hats.set(slot, hat);
    return true;
  }

  /** Whether a hat exists and its achievement is unlocked. */
  public boolean canWear(String hat) {
    if (hat.isEmpty()) {
      return true;
    }
    CareerConfig.Achievement a = config.achievement(hat);
    return a != null && a.hat() != null && achievements.contains(hat);
  }

  /** An independent copy, for what-if simulations. */
  public Career copy(GameData data) {
    return restore(data, snapshot());
  }
}
