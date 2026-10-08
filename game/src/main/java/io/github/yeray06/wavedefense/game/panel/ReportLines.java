package io.github.yeray06.wavedefense.game.panel;

import io.github.yeray06.wavedefense.game.i18n.ScriptTexts;
import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.sim.data.Challenge;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.progress.Career;
import io.github.yeray06.wavedefense.sim.progress.CareerOutcome;
import io.github.yeray06.wavedefense.sim.progress.Goal;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.WaveOutcome;
import io.github.yeray06.wavedefense.sim.progress.WaveRecord;
import io.github.yeray06.wavedefense.sim.world.WaveReport;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The after-wave report as text (plan, "Informe" and "Derrota y reinicio"): the stars earned and
 * the wave's three goals (D-57), the enemies that got through and by which part of the path, and
 * per turret its shots, the exceptions with their line and how long it hung.
 */
public final class ReportLines {
  /** How a line is shown. */
  public enum Kind {
    TITLE,
    TEXT,
    GOOD,
    WARNING
  }

  /**
   * One line of the report.
   *
   * @param detail an indented line under the one before
   */
  public record Line(String text, Kind kind, boolean detail) {
    Line(String text, Kind kind) {
      this(text, kind, false);
    }
  }

  /** Positions on the path are grouped to this many percent, so the list stays short. */
  private static final int PATH_STEP_PERCENT = 5;

  private ReportLines() {}

  public static List<Line> build(
      WaveReport report,
      WaveOutcome outcome,
      Progress progress,
      Challenge challenge,
      ScriptTexts names,
      int ticksPerSecond,
      double pathLength) {
    Texts texts = names.texts();
    List<Line> lines = new ArrayList<>();
    String title =
        !outcome.won()
            ? "report.title.lost"
            : outcome.perfect() ? "report.title.perfect" : "report.title.won";
    lines.add(new Line(texts.format(title, report.wave()), Kind.TITLE));

    lines.add(
        outcome.stars() > 0
            ? new Line(texts.format("report.stars", names.stars(outcome.stars())), Kind.GOOD)
            : new Line(texts.get("report.stars.none"), Kind.TEXT));
    for (Goal goal : Goal.values()) {
      boolean met = progress.met(report.wave(), goal);
      String text = names.goalLine(goal, challenge, met);
      if (goal == Goal.CHALLENGE && !outcome.challengeMet()) {
        text = texts.format("report.challenge.got", text, outcome.challengeValue());
      }
      if (outcome.newGoals().contains(goal)) {
        text = texts.format("report.goal.new", text);
      }
      lines.add(new Line(text, met ? Kind.GOOD : Kind.TEXT, true));
    }
    if (outcome.trophies() > 0) {
      lines.add(new Line(texts.format("report.trophy", progress.trophies()), Kind.GOOD));
    }
    if (!outcome.won()) {
      lines.add(new Line(texts.format("report.next.lost", outcome.nextWave()), Kind.WARNING));
    }

    addDetails(lines, report, names, ticksPerSecond, pathLength);
    return lines;
  }

  /**
   * «Probar» (D-65): the report of a trial run, which earns and records nothing: whether it would
   * win, the leaks and each turret.
   */
  public static List<Line> trial(
      WaveReport report, ScriptTexts names, int ticksPerSecond, double pathLength) {
    Texts texts = names.texts();
    List<Line> lines = new ArrayList<>();
    String title = !report.won() ? "trial.lost" : report.perfect() ? "trial.perfect" : "trial.won";
    lines.add(new Line(texts.format(title, report.wave()), Kind.TITLE));
    lines.add(new Line(texts.get("trial.note"), Kind.TEXT, true));
    addDetails(lines, report, names, ticksPerSecond, pathLength);
    return lines;
  }

  /** What a wave added to the career (D-63): new records, the streak and achievements. */
  public static List<Line> career(
      CareerOutcome outcome, Career career, int wave, ScriptTexts names, int ticksPerSecond) {
    Texts texts = names.texts();
    List<Line> lines = new ArrayList<>();
    WaveRecord record = career.record(wave);
    if (!outcome.newMarks().isEmpty() && record != null) {
      lines.add(new Line(texts.get("report.records"), Kind.GOOD));
      for (WaveRecord.Mark mark : WaveRecord.Mark.values()) {
        if (outcome.newMarks().contains(mark)) {
          lines.add(new Line(recordText(mark, record, names, ticksPerSecond), Kind.GOOD, true));
        }
      }
    }
    if (outcome.streak() > 0) {
      lines.add(
          new Line(
              texts.format(
                  "report.streak", outcome.streak(), outcome.streakPoints(), career.streakScore()),
              Kind.TEXT));
    }
    for (String id : outcome.newAchievements()) {
      lines.add(
          new Line(
              texts.format("career.toast", texts.get("achievement." + id + ".name")), Kind.GOOD));
    }
    return lines;
  }

  /** One of the player's marks on a wave, as text. */
  public static String recordText(
      WaveRecord.Mark mark, WaveRecord record, ScriptTexts names, int ticksPerSecond) {
    Texts texts = names.texts();
    return switch (mark) {
      case TICKS ->
          texts.format("record.ticks", oneDecimal(record.ticks() / (double) ticksPerSecond));
      case SHOTS -> texts.format("record.shots", record.shots());
      case MEMORY -> texts.format("record.memory", record.memory());
    };
  }

  private static void addDetails(
      List<Line> lines,
      WaveReport report,
      ScriptTexts names,
      int ticksPerSecond,
      double pathLength) {
    Texts texts = names.texts();
    addLeaks(lines, report, names, pathLength);

    for (int i = 0; i < report.turrets().size(); i++) {
      WaveReport.TurretReport turret = report.turrets().get(i);
      lines.add(
          new Line(
              texts.format(
                  "report.turret",
                  i + 1,
                  turret.shots(),
                  turret.airShots(),
                  turret.wastedOnDead(),
                  turret.reloads()),
              Kind.TEXT));
      if (turret.faults().isEmpty()) {
        lines.add(new Line(texts.get("report.turret.clean"), Kind.GOOD, true));
        continue;
      }
      lines.add(
          new Line(
              texts.format(
                  "report.turret.hung", oneDecimal(turret.hungTicks() / (double) ticksPerSecond)),
              Kind.WARNING,
              true));
      for (WaveReport.FaultCount fault : turret.faults()) {
        lines.add(
            new Line(
                texts.format(
                    fault.inLibrary() ? "report.turret.faultLibrary" : "report.turret.fault",
                    names.fault(fault.type()),
                    fault.line(),
                    fault.count()),
                Kind.WARNING,
                true));
      }
    }
  }

  private static void addLeaks(
      List<Line> lines, WaveReport report, ScriptTexts names, double pathLength) {
    Texts texts = names.texts();
    if (report.leaks().isEmpty()) {
      lines.add(new Line(texts.get("report.leaks.none"), Kind.GOOD));
      return;
    }
    lines.add(new Line(texts.format("report.leaks", report.leaks().size()), Kind.WARNING));
    Map<EnemyKind, Integer> byKind = new EnumMap<>(EnemyKind.class);
    Map<Integer, Integer> byPlace = new TreeMap<>();
    for (WaveReport.Leak leak : report.leaks()) {
      byKind.merge(leak.kind(), 1, Integer::sum);
      int place =
          leak.lastDefendedDistance() < 0
              ? -1
              : (int) Math.round(leak.lastDefendedDistance() / pathLength * 100 / PATH_STEP_PERCENT)
                  * PATH_STEP_PERCENT;
      byPlace.merge(place, 1, Integer::sum);
    }
    byKind.forEach(
        (kind, count) ->
            lines.add(
                new Line(
                    texts.format("report.leak.group", count, names.enemyName(kind)),
                    Kind.TEXT,
                    true)));
    byPlace.forEach(
        (place, count) -> {
          String where =
              place < 0 ? texts.get("report.leak.never") : texts.format("report.leak.at", place);
          lines.add(
              new Line(
                  count > 1
                      ? texts.format("report.leak.where.many", where, count)
                      : texts.format("report.leak.where", where),
                  Kind.TEXT,
                  true));
        });
  }

  /** "6" for 6.0, "2,5" for 2.5. */
  static String oneDecimal(double value) {
    long tenths = Math.round(value * 10);
    return tenths % 10 == 0 ? String.valueOf(tenths / 10) : (tenths / 10) + "," + (tenths % 10);
  }
}
