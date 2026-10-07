package io.github.yeray06.wavedefense.simcli;

import io.github.yeray06.wavedefense.sim.data.GameData;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Plays the MVP with the reference player and reports the balance.
 *
 * <pre>
 * ./gradlew :sim-cli:run                                   # summary only
 * ./gradlew :sim-cli:run --args="--write docs/BALANCE.md"  # also writes the report
 * ./gradlew :sim-cli:run --args="--assets other/assets"    # another data set
 * ./gradlew :sim-cli:run --args="--sweep-regenerator FILE" # Regenerator sweep (RegeneratorSweep)
 * </pre>
 *
 * <p>Exits with 0 even when the plan's rules are not met: those are findings, not failures.
 */
public final class SimMain {
  private SimMain() {}

  public static void main(String[] args) throws IOException {
    Path assets = Path.of("assets");
    Path write = null;
    Path sweep = null;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--assets" -> assets = Path.of(args[++i]);
        case "--write" -> write = Path.of(args[++i]);
        case "--sweep-regenerator" -> sweep = Path.of(args[++i]);
        default -> {
          System.err.println("Unknown argument: " + args[i]);
          System.err.println("Usage: [--assets DIR] [--write FILE] [--sweep-regenerator FILE]");
          System.exit(2);
        }
      }
    }
    PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    if (sweep != null) {
      RegeneratorSweep regenerator = new RegeneratorSweep(assets, PlayerConfig.load());
      Files.writeString(sweep, regenerator.render(regenerator.sweep()), StandardCharsets.UTF_8);
      out.println("Wrote " + sweep);
      return;
    }
    Path root = assets;
    GameData data = GameData.load(file -> Files.readString(root.resolve(file)));
    PlayerConfig config = PlayerConfig.load();
    ReferencePlayer player = new ReferencePlayer(data, config);

    ProgressionRun.Result run = new ProgressionRun(data, player, config).run();
    List<DoubleRule.Entry> doubleRule =
        new DoubleRule(data, player, config).evaluate(run.beforeBuying());
    BalanceReport report = new BalanceReport(data, assets, config, player);
    List<DoubleRule.Entry> doubleRuleWithoutCaps =
        new DoubleRule(data.withoutHardwareCaps(), player, config).evaluate(run.beforeBuying());
    String markdown =
        report.render(
            run,
            new ProgressionRun(data, player, config, false).run(),
            doubleRule,
            doubleRuleWithoutCaps);

    out.printf(
        "Waves played: %d, boss defeated: %s, locked at: %s, play time: %.1f min,"
            + " stars earned: %d, left: %d%n",
        run.rows().size(),
        run.bossDefeated(),
        run.lockedAt(),
        run.playSeconds() / 60,
        run.finalProgress().starsEarned(),
        run.finalProgress().stars());
    for (ProgressionRun.Wall wall : run.walls()) {
      out.printf(
          "Wall at wave %d: %d attempts, %d passes%n", wall.wave(), wall.attempts(), wall.passes());
    }
    for (DoubleRule.Entry e : doubleRule) {
      DoubleRule.Entry free =
          doubleRuleWithoutCaps.stream()
              .filter(u -> u.feature() == e.feature())
              .findFirst()
              .orElse(e);
      out.printf(
          "Double rule %s (wave %d): with %.2f [%s], alone %.2f, capped hw %.2f (%s) ratio %.2f;"
              + " free hw %.2f (%s) ratio %.2f%n",
          e.feature(),
          e.wave(),
          e.kWith(),
          e.scriptWith(),
          e.kNeither(),
          e.kWithout(),
          e.bestHardware(),
          e.ratio(),
          free.kWithout(),
          free.bestHardware(),
          free.ratio());
    }
    out.println("Contradictions with the plan: " + report.contradictions().size());
    for (String c : report.contradictions()) {
      out.println(" - " + c);
    }
    if (write != null) {
      Files.writeString(write, markdown, StandardCharsets.UTF_8);
      out.println("Wrote " + write);
    }
  }
}
