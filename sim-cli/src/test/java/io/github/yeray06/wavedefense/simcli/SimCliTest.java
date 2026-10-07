package io.github.yeray06.wavedefense.simcli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yeray06.wavedefense.script.CompileResult;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.sim.data.GameData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SimCliTest {
  private static final Path ASSETS = Path.of("assets");
  private static GameData data;
  private static PlayerConfig config;
  private static ReferencePlayer player;

  @BeforeAll
  static void load() {
    data = GameData.load(file -> Files.readString(ASSETS.resolve(file)));
    config = PlayerConfig.load();
    player = new ReferencePlayer(data, config);
  }

  @Test
  void everyReferenceScriptFitsTheTurretsMemoryWithTheSyntaxItClaims() {
    record Expectation(String name, List<Feature> features, int wave) {}
    List<Expectation> expectations =
        List.of(
            new Expectation("L0", List.of(), 1),
            new Expectation("L1", List.of(Feature.IF_ELSE), 1),
            new Expectation("L2", List.of(Feature.IF_ELSE, Feature.VARIABLES), 1),
            new Expectation("L2-ammo", List.of(Feature.IF_ELSE, Feature.VARIABLES), 6),
            new Expectation(
                "L3-ammo", List.of(Feature.IF_ELSE, Feature.VARIABLES, Feature.LOGIC_OPS), 6),
            new Expectation(
                "L4",
                List.of(Feature.IF_ELSE, Feature.VARIABLES, Feature.LOGIC_OPS, Feature.WHILE),
                1),
            new Expectation(
                "L4-ammo",
                List.of(Feature.IF_ELSE, Feature.VARIABLES, Feature.LOGIC_OPS, Feature.WHILE),
                6));
    for (Expectation e : expectations) {
      CompileResult result =
          data.compiler()
              .compile(
                  ReferencePlayer.resource(e.name()),
                  new Unlocks(java.util.Set.copyOf(e.features()), e.wave()),
                  data.turret().memoryLines());
      assertTrue(result.succeeded(), () -> e.name() + ": " + result.errors());
    }
  }

  @Test
  void playersPickTheBestScriptTheirSyntaxAllows() {
    assertEquals("L0", player.script(Unlocks.of(1)).name());
    assertEquals("L1", player.script(Unlocks.of(1, Feature.IF_ELSE)).name());
    assertEquals(
        "L2-ammo", player.script(Unlocks.of(6, Feature.IF_ELSE, Feature.VARIABLES)).name());
    assertEquals("L2", player.script(Unlocks.of(5, Feature.IF_ELSE, Feature.VARIABLES)).name());
  }

  @Test
  void theWholeRunIsDeterministicAndReachesTheBoss() {
    ProgressionRun.Result first = new ProgressionRun(data, player, config).run();
    ProgressionRun.Result second = new ProgressionRun(data, player, config).run();
    assertEquals(first.rows(), second.rows());
    assertEquals(first.walls(), second.walls());
    assertTrue(first.bossDefeated());
  }

  @Test
  void theCommittedBalanceReportIsUpToDate() throws IOException {
    ProgressionRun.Result run = new ProgressionRun(data, player, config).run();
    List<DoubleRule.Entry> doubleRule =
        new DoubleRule(data, player, config).evaluate(run.beforeBuying());
    List<DoubleRule.Entry> withoutCaps =
        new DoubleRule(data.withoutHardwareCaps(), player, config).evaluate(run.beforeBuying());
    String markdown =
        new BalanceReport(data, ASSETS, config, player)
            .render(
                run,
                new ProgressionRun(data, player, config, false).run(),
                doubleRule,
                withoutCaps);
    assertEquals(
        Files.readString(Path.of("docs/BALANCE.md")),
        markdown,
        "docs/BALANCE.md is stale: run ./gradlew :sim-cli:run --args=\"--write docs/BALANCE.md\"");
  }

  @Test
  void theRegeneratorSweepMeasuresTheCurrentDataLikeTheReportAndLeavesWavesOneToThreeAlone() {
    RegeneratorSweep sweep = new RegeneratorSweep(ASSETS, config);
    RegeneratorSweep.Setting current = sweep.current();
    RegeneratorSweep.Result now = sweep.measure(current);
    ProgressionRun.Result run = new ProgressionRun(data, player, config).run();
    DoubleRule.Entry variables =
        new DoubleRule(data, player, config)
            .evaluate(run.beforeBuying()).stream()
                .filter(e -> e.feature() == Feature.VARIABLES)
                .findFirst()
                .orElseThrow();
    assertEquals(variables.ratio(), now.ratio());
    assertTrue(now.earlyWavesUnchanged());

    RegeneratorSweep.Result changed =
        sweep.measure(
            new RegeneratorSweep.Setting(
                current.delay() / 2, current.rate() * 2, current.health() + 1, current.gap() / 2));
    assertTrue(changed.earlyWavesUnchanged(), "waves 1-3 have no regenerators");
    assertTrue(sweep.render(List.of(now, changed)).contains("| actual: "));
  }
}
