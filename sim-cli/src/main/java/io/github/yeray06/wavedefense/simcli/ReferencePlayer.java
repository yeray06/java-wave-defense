package io.github.yeray06.wavedefense.simcli;

import io.github.yeray06.wavedefense.script.CompileResult;
import io.github.yeray06.wavedefense.script.LibraryResult;
import io.github.yeray06.wavedefense.script.ScriptCompiler;
import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.script.check.Library;
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
import java.util.Optional;

/**
 * A player who writes the best reference script their syntax allows and clicks reload by hand when
 * a turret runs dry. Scripts live in {@code reference/scripts/L<n>[-ammo].txt}, where {@code n} is
 * how many shop pieces are owned and {@code -ammo} uses the wave-6 ammo switch. A script with
 * methods has its library next to it, in {@code L<n>…lib.txt}.
 *
 * <p>The player reads each wave's briefing and rewrites one condition for it: {@value
 * #PIERCING_IF}, in a script or library, becomes when to use piercing ammo on the target {@code o}:
 * armored, or a Mutant weak to it. Without methods that line is in every turret's script; with
 * them, in the library once.
 */
final class ReferencePlayer {
  /** Written where a script decides to use piercing ammo on {@code o}. */
  static final String PIERCING_IF = "{PERFORANTE_SI}";

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
        load(base + suffix, unlocks, memory).ifPresent(found::add);
      }
      if (!found.isEmpty()) {
        return List.copyOf(found);
      }
    }
    throw new IllegalStateException("no reference script compiles with " + unlocks);
  }

  /** A reference script and its library, if both exist and compile with these unlocks. */
  private Optional<ChosenScript> load(String name, Unlocks unlocks, int memory) {
    String source = resource(name);
    if (source == null) {
      return Optional.empty();
    }
    String library = resource(name + ".lib");
    library = library == null ? "" : library;
    String condition = piercingIf(null);
    LibraryResult compiledLibrary =
        compiler.compileLibrary(library.replace(PIERCING_IF, condition), unlocks);
    if (!compiledLibrary.succeeded()) {
      return Optional.empty();
    }
    CompileResult result =
        compiler.compile(
            source.replace(PIERCING_IF, condition), compiledLibrary.library(), unlocks, memory);
    if (!result.succeeded()) {
      return Optional.empty();
    }
    return Optional.of(new ChosenScript(name, source, library, result.memoryUsed(), unlocks));
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
        load(base + suffix, unlocks, memory).ifPresent(found::add);
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
    String condition = piercingIf(data.waves().wave(wave).mutantWeakness());
    Library library =
        compiler
            .compileLibrary(script.library().replace(PIERCING_IF, condition), script.unlocks())
            .library();
    String source = script.source().replace(PIERCING_IF, condition);
    List<CompiledScript> scripts = new ArrayList<>();
    for (int i = 0; i < turrets; i++) {
      // One compiled instance per turret: a CompiledScript is not shared between turrets.
      scripts.add(
          compiler
              .compile(
                  source,
                  library,
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

  /**
   * When to use piercing ammo on {@code o}, for Mutants weak to {@code weakness} ({@code null}
   * without Mutants): always on armor, and on a Mutant only if piercing is its weak spot.
   */
  private static String piercingIf(AmmoType weakness) {
    return weakness == AmmoType.PIERCING
        ? "o.blindado() || o.mutante()"
        : "o.blindado() && !o.mutante()";
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

  /**
   * @param library the library the script calls; empty without methods
   */
  record ChosenScript(
      String name, String source, String library, int memoryUsed, Unlocks unlocks) {}
}
