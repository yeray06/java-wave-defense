package io.github.yeray06.wavedefense.simcli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.sim.data.EnemyCatalog;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.data.GameData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sweeps the Regenerator's four numbers (heal delay, heal rate, health, spawn gap) and measures the
 * double rule for Variables, plus the walls of a whole game, for each combination. The data on disk
 * is never changed: each combination patches {@code enemies.json} in memory.
 *
 * <pre>
 * ./gradlew :sim-cli:run --args="--sweep-regenerator docs/BARRIDO-REGENERADOR.md"
 * </pre>
 */
final class RegeneratorSweep {
  /** The sweep grid: two rounds, the second around the best of the first. */
  private static final double[][][] ROUNDS = {
    {{1, 0.75, 0.5}, {0.5, 1, 2, 4}, {1, 2, 3, 4}, {1, 0.5, 0.25}},
    {{1, 0.5}, {0.5, 2}, {0.75, 1, 1.25, 1.5}, {1, 1.5, 2, 3}}
  };

  /** One Regenerator setting. */
  record Setting(double delay, double rate, double health, double gap) {}

  /**
   * The result of one setting.
   *
   * @param kWith Variables with its script
   * @param kNeither neither Variables nor hardware
   * @param kHardware no Variables, best capped hardware for the same money
   * @param freeRatio the ratio against uncapped hardware
   * @param earlyPasses most passes at a wall on waves 4–5
   * @param earlyWavesUnchanged waves 1–3 play exactly as with the current data
   */
  record Result(
      Setting setting,
      double kWith,
      double kNeither,
      double kHardware,
      double ratio,
      double freeRatio,
      int earlyPasses,
      String walls,
      double minutes,
      boolean bossDefeated,
      boolean earlyWavesUnchanged) {}

  private final Path assets;
  private final PlayerConfig config;
  private final String enemiesJson;
  private final ObjectMapper mapper = new ObjectMapper();
  private final String baselineEarlyWaves;

  RegeneratorSweep(Path assets, PlayerConfig config) {
    this.assets = assets;
    this.config = config;
    this.enemiesJson = read(GameData.ENEMIES);
    this.baselineEarlyWaves = earlyWaves(run(load(enemiesJson)));
  }

  /** The Regenerator as the data on disk has it. */
  Setting current() {
    EnemyCatalog.EnemyType type = load(enemiesJson).enemies().type(EnemyKind.REGENERATOR);
    return new Setting(
        type.regenDelaySeconds(),
        type.regenFractionPerSecond(),
        type.healthMultiplier(),
        type.spawnGapFactor());
  }

  List<Result> sweep() {
    List<Result> results = new ArrayList<>();
    results.add(measure(current()));
    for (double[][] round : ROUNDS) {
      for (double delay : round[0]) {
        for (double rate : round[1]) {
          for (double health : round[2]) {
            for (double gap : round[3]) {
              results.add(measure(new Setting(delay, rate, health, gap)));
            }
          }
        }
      }
    }
    return results;
  }

  Result measure(Setting setting) {
    GameData data = load(patch(setting));
    ReferencePlayer player = new ReferencePlayer(data, config);
    ProgressionRun.Result run = run(data, player);
    DoubleRule.Entry capped = variables(new DoubleRule(data, player, config), run);
    DoubleRule.Entry free =
        variables(new DoubleRule(data.withoutHardwareCaps(), player, config), run);
    StringBuilder walls = new StringBuilder();
    int earlyPasses = 0;
    for (ProgressionRun.Wall wall : run.walls()) {
      walls.append(walls.isEmpty() ? "" : ", ").append(wall.wave()).append(" (");
      walls.append(wall.passes()).append(')');
      if (wall.wave() == 4 || wall.wave() == 5) {
        earlyPasses = Math.max(earlyPasses, wall.passes());
      }
    }
    return new Result(
        setting,
        capped.kWith(),
        capped.kNeither(),
        capped.kWithout(),
        capped.ratio(),
        free.ratio(),
        earlyPasses,
        walls.isEmpty() ? "—" : walls.toString(),
        run.playSeconds() / 60,
        run.bossDefeated(),
        earlyWaves(run).equals(baselineEarlyWaves));
  }

  String render(List<Result> results) {
    StringBuilder md = new StringBuilder();
    md.append("# Barrido del Regenerador\n\n");
    md.append("Generado con `./gradlew :sim-cli:run --args=\"--sweep-regenerator FILE\"`.\n");
    md.append("Objetivo por defecto: el de `turret.json`. Cada fila es una partida entera del\n");
    md.append(
        "jugador de referencia y la regla del doble de Variables en la oleada 4 (sección 8\n");
    md.append("de `docs/BALANCE.md`). La primera fila son los datos actuales.\n\n");
    md.append("- **Espera**: segundos sin daño antes de curarse. **Curación**: fracción de la\n");
    md.append("  vida máxima por segundo. **Vida**: respecto al Básico. **Separación**: hueco\n");
    md.append("  tras cada Regenerador al salir, respecto al reparto uniforme (1).\n");
    md.append("- **k**: mayor multiplicador de vida con el que la oleada sale sin fugas. Con\n");
    md.append(
        "  Variables / sin nada / sin Variables y con el mejor hardware de las mismas estrellas.\n");
    md.append("- **Ratio**: k con Variables entre k con hardware (con topes; entre paréntesis,\n");
    md.append("  sin topes). Cumple la regla del doble si es ≥ 2.\n\n");
    md.append("| Espera | Curación | Vida | Separación | k Variables | k nada | k hardware |");
    md.append(" Ratio | Pasadas 4–5 | Muros (pasadas) | Min | Oleadas 1–3 iguales |\n");
    md.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
    for (int i = 0; i < results.size(); i++) {
      Result r = results.get(i);
      Setting s = r.setting();
      md.append(
          String.format(
              Locale.ROOT,
              "| %s%s | %s | %s | %s | %s | %s | %s | **%s** (%s) | %d | %s | %s | %s |%n",
              i == 0 ? "actual: " : "",
              decimal(s.delay()),
              decimal(s.rate()),
              decimal(s.health()),
              decimal(s.gap()),
              decimal(r.kWith()),
              decimal(r.kNeither()),
              decimal(r.kHardware()),
              decimal(r.ratio()),
              decimal(r.freeRatio()),
              r.earlyPasses(),
              r.walls(),
              String.format(Locale.ROOT, "%.1f", r.minutes()).replace('.', ','),
              r.earlyWavesUnchanged() ? "sí" : "**no**"));
    }
    return md.toString();
  }

  private String patch(Setting setting) {
    try {
      ObjectNode root = (ObjectNode) mapper.readTree(enemiesJson);
      ObjectNode type = (ObjectNode) root.path("types").path(EnemyKind.REGENERATOR.name());
      type.put("regenDelaySeconds", setting.delay());
      type.put("regenFractionPerSecond", setting.rate());
      type.put("healthMultiplier", setting.health());
      type.put("spawnGapFactor", setting.gap());
      return mapper.writeValueAsString(root);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private GameData load(String enemies) {
    return GameData.load(file -> file.equals(GameData.ENEMIES) ? enemies : read(file));
  }

  private ProgressionRun.Result run(GameData data) {
    return run(data, new ReferencePlayer(data, config));
  }

  private ProgressionRun.Result run(GameData data, ReferencePlayer player) {
    return new ProgressionRun(data, player, config).run();
  }

  /** Every wave played before wave 4 is first reached: number, won, leaks and payout. */
  private static String earlyWaves(ProgressionRun.Result run) {
    StringBuilder early = new StringBuilder();
    for (ProgressionRun.Row row : run.rows()) {
      if (row.wave() >= 4) {
        break;
      }
      early.append(row.wave()).append(row.won()).append(row.leaks()).append(row.earned());
      early.append(';');
    }
    return early.toString();
  }

  private static DoubleRule.Entry variables(DoubleRule rule, ProgressionRun.Result run) {
    return rule.evaluate(run.beforeBuying()).stream()
        .filter(e -> e.feature() == Feature.VARIABLES)
        .findFirst()
        .orElseThrow();
  }

  private String read(String file) {
    try {
      return Files.readString(assets.resolve(file));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String decimal(double value) {
    String text = String.format(Locale.ROOT, "%.2f", value);
    text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
    return text.replace('.', ',');
  }
}
