package io.github.yeray06.wavedefense.simcli;

import io.github.yeray06.wavedefense.script.CompileResult;
import io.github.yeray06.wavedefense.script.ScriptCompiler;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.ShopConfig;
import io.github.yeray06.wavedefense.sim.world.Battle;
import io.github.yeray06.wavedefense.sim.world.BattleSetup;
import io.github.yeray06.wavedefense.sim.world.Loadout;
import io.github.yeray06.wavedefense.sim.world.Turret;
import io.github.yeray06.wavedefense.sim.world.WaveReport;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A player who writes the best reference script their syntax allows and clicks reload by hand when
 * a turret runs dry. Scripts live in {@code reference/scripts/L<n>[-ammo].txt}, where {@code n} is
 * how many shop pieces are owned and {@code -ammo} uses the wave-6 ammo switch.
 */
final class ReferencePlayer {
  private final GameData data;
  private final PlayerConfig config;
  private final ScriptCompiler compiler;

  ReferencePlayer(GameData data, PlayerConfig config) {
    this.data = data;
    this.config = config;
    this.compiler = data.compiler();
  }

  /**
   * The script a player with these unlocks runs in the campaign: the first of {@link #variants}.
   */
  ChosenScript script(Unlocks unlocks) {
    return script(unlocks, data.turret().memoryLines());
  }

  /** The campaign script for these unlocks and {@code memory} lines (memory upgrades). */
  ChosenScript script(Unlocks unlocks, int memory) {
    return variants(unlocks, memory).get(0);
  }

  /**
   * Every variant of the best script level these unlocks allow ({@code name}, {@code name-b},
   * {@code name-c}…). With 12 memory lines a player sometimes has to choose what to leave out; the
   * double rule tries each choice so the comparison is fair.
   */
  List<ChosenScript> variants(Unlocks unlocks) {
    return variants(unlocks, data.turret().memoryLines());
  }

  List<ChosenScript> variants(Unlocks unlocks, int memory) {
    for (String base : candidates(level(unlocks))) {
      List<ChosenScript> found = new ArrayList<>();
      for (String suffix : new String[] {"", "-b", "-c"}) {
        String name = base + suffix;
        String source = resource(name);
        if (source == null) {
          continue;
        }
        CompileResult result = compiler.compile(source, unlocks, memory);
        if (result.succeeded()) {
          found.add(new ChosenScript(name, source, result.memoryUsed(), unlocks));
        }
      }
      if (!found.isEmpty()) {
        return List.copyOf(found);
      }
    }
    throw new IllegalStateException("no reference script compiles with " + unlocks);
  }

  /**
   * Every reference script these unlocks compile, strongest first: what a player could write when a
   * code challenge asks for something else than the best script.
   */
  List<ChosenScript> allScripts(Unlocks unlocks) {
    return allScripts(unlocks, data.turret().memoryLines());
  }

  List<ChosenScript> allScripts(Unlocks unlocks, int memory) {
    List<ChosenScript> found = new ArrayList<>();
    for (String base : candidates(level(unlocks))) {
      for (String suffix : new String[] {"", "-b", "-c"}) {
        String source = resource(base + suffix);
        if (source == null) {
          continue;
        }
        CompileResult result = compiler.compile(source, unlocks, memory);
        if (result.succeeded()) {
          found.add(new ChosenScript(base + suffix, source, result.memoryUsed(), unlocks));
        }
      }
    }
    return found;
  }

  private static List<String> candidates(int level) {
    List<String> candidates = new ArrayList<>();
    for (int l = level; l >= 0; l--) {
      candidates.add("L" + l + "-ammo");
    }
    for (int l = level; l >= 0; l--) {
      candidates.add("L" + l);
    }
    return candidates;
  }

  /** How many shop pieces, in the plan's order, are owned without gaps. */
  int level(Unlocks unlocks) {
    int level = 0;
    for (ShopConfig.SyntaxItem item : data.shop().syntax()) {
      if (!item.inMvp() || !unlocks.has(item.feature())) {
        break;
      }
      level++;
    }
    return level;
  }

  /** Plays one wave, clicking reload on empty turrets after the reaction time. */
  WaveReport play(int wave, Unlocks unlocks, int turrets, Loadout loadout, double healthScale) {
    return play(wave, script(unlocks), turrets, loadout, healthScale);
  }

  /** Plays one wave with a given script on every turret. */
  WaveReport play(int wave, ChosenScript script, int turrets, Loadout loadout, double healthScale) {
    List<CompiledScript> scripts = new ArrayList<>();
    for (int i = 0; i < turrets; i++) {
      // One compiled instance per turret: a CompiledScript is not shared between turrets.
      scripts.add(
          compiler
              .compile(
                  script.source(),
                  script.unlocks(),
                  Math.max(data.turret().memoryLines(), script.memoryUsed()))
              .script()
              .orElseThrow());
    }
    Battle battle = new Battle(data, new BattleSetup(wave, scripts, loadout, healthScale, wave));
    int reactionTicks =
        (int) Math.round(config.manualReloadReactionSeconds() * battle.ticksPerSecond());
    int[] emptyFor = new int[turrets];
    while (!battle.finished()) {
      battle.tick();
      for (int i = 0; i < turrets; i++) {
        Turret turret = battle.turret(i);
        boolean waiting = turret.ammo() == 0 && !turret.reloading();
        emptyFor[i] = waiting ? emptyFor[i] + 1 : 0;
        if (emptyFor[i] >= reactionTicks && battle.manualReload(i)) {
          emptyFor[i] = 0;
        }
      }
    }
    return battle.report();
  }

  static String resource(String name) {
    try (InputStream in =
        ReferencePlayer.class.getResourceAsStream("/reference/scripts/" + name + ".txt")) {
      return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Features of the MVP in shop order. */
  List<Feature> mvpFeatures() {
    return data.shop().syntax().stream()
        .filter(ShopConfig.SyntaxItem::inMvp)
        .map(ShopConfig.SyntaxItem::feature)
        .toList();
  }

  record ChosenScript(String name, String source, int memoryUsed, Unlocks unlocks) {}
}
