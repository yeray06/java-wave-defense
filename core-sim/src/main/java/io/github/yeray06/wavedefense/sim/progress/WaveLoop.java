package io.github.yeray06.wavedefense.sim.progress;

import io.github.yeray06.wavedefense.script.CompileResult;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.world.Battle;
import io.github.yeray06.wavedefense.sim.world.BattleSetup;
import io.github.yeray06.wavedefense.sim.world.WaveReport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The game's wave loop (D-01): one battle after another, each finished wave applied to the player's
 * progress.
 *
 * <ul>
 *   <li>A wave never beaten waits in {@link State#AWAITING_DEPLOY} until {@link #deploy()}.
 *   <li>Any other wave starts as soon as the previous one ends: repetitions with auto-advance off,
 *       and already beaten waves with it on (D-01b).
 * </ul>
 *
 * <p>While a wave waits, {@link #battle()} is that wave built but not started, so the field can be
 * drawn. Each turret runs its own script; one that does not compile leaves the turret idle. Battles
 * are seeded with a counter, so a game replays identically from the same inputs.
 */
public final class WaveLoop {
  /** Where the loop is. */
  public enum State {
    AWAITING_DEPLOY,
    RUNNING
  }

  private final GameData data;
  private final Progress progress;
  private final Progression progression;
  private final List<String> sources;
  private final List<String> drafts;
  private Battle battle;
  private State state;
  private WaveReport lastReport;
  private WaveOutcome lastOutcome;
  private int wavesFinished;
  private long battlesBuilt;
  private long revision;

  /** The most memory any turret's code takes in the battle built: for the code challenge. */
  private int memoryUsed;

  /** Starts from {@code progress}; every turret runs the data's initial script (D-14). */
  public WaveLoop(GameData data, Progress progress) {
    this.data = data;
    this.progress = progress;
    this.progression = new Progression(data);
    int slots = data.map().turretSlots().size();
    this.sources = new ArrayList<>(Collections.nCopies(slots, data.turret().initialScript()));
    this.drafts = new ArrayList<>(sources);
    next();
  }

  /**
   * A saved game: its progress, the code each turret runs and what the editor held. The current
   * wave waits for {@link #deploy()} even if it was beaten, so loading never starts a wave by
   * itself. A saved script that no longer compiles (the data changed) falls back to the initial
   * script; its draft is kept so the player sees their code.
   */
  public static WaveLoop restore(
      GameData data, Progress progress, List<String> scripts, List<String> drafts, long battles) {
    WaveLoop loop = new WaveLoop(data, progress);
    for (int i = 0; i < loop.sources.size(); i++) {
      if (i < scripts.size() && loop.compile(scripts.get(i)).succeeded()) {
        loop.sources.set(i, scripts.get(i));
      }
      loop.drafts.set(i, i < drafts.size() ? drafts.get(i) : loop.sources.get(i));
    }
    loop.battlesBuilt = battles;
    loop.build(true);
    return loop;
  }

  /** Advances the running wave one tick; when it ends, applies it and moves on. */
  public void tick() {
    if (state != State.RUNNING) {
      return;
    }
    battle.tick();
    if (battle.finished()) {
      lastReport = battle.report();
      lastOutcome = progression.apply(progress, lastReport, memoryUsed);
      wavesFinished++;
      revision++;
      next();
    }
  }

  /** The player starts a wave that was waiting; ignored otherwise. */
  public void deploy() {
    if (state == State.AWAITING_DEPLOY) {
      state = State.RUNNING;
    }
  }

  /**
   * The player's emergency reload on a turret (D-03). Only while a wave runs.
   *
   * @return whether the reload started
   */
  public boolean manualReload(int turret) {
    return state == State.RUNNING && turret < battle.turretCount() && battle.manualReload(turret);
  }

  /**
   * The player's code for a turret slot (plan, "Edición en caliente"). If it compiles it replaces
   * the slot's script: a waiting wave is rebuilt with it, a running one keeps its code and the new
   * one enters the next wave. If it does not compile, the slot keeps its last compiling script.
   *
   * @return the compilation, for the editor's errors and memory counter
   */
  public CompileResult setScript(int slot, String source) {
    if (!source.equals(drafts.get(slot))) {
      drafts.set(slot, source);
      revision++;
    }
    CompileResult result = compile(source);
    if (result.succeeded()) {
      sources.set(slot, source);
      if (state == State.AWAITING_DEPLOY) {
        build(true);
      }
    }
    return result;
  }

  /** The script a slot runs: the last one that compiled, or the initial script (D-14). */
  public String script(int slot) {
    return sources.get(slot);
  }

  /** What the editor holds for a slot: the last code given to {@link #setScript}, even broken. */
  public String draft(int slot) {
    return drafts.get(slot);
  }

  /** Every slot's script, in slot order, for saving. */
  public List<String> scripts() {
    return List.copyOf(sources);
  }

  /** Every slot's draft, in slot order, for saving. */
  public List<String> drafts() {
    return List.copyOf(drafts);
  }

  /** Battles built so far: the seed of the next one. Saved so a loaded game replays the same. */
  public long battlesBuilt() {
    return battlesBuilt;
  }

  /**
   * Grows whenever something worth saving changes: code, purchases, a finished wave, the wave
   * chosen on the map or the auto-advance switch. Waves in progress are not saved.
   */
  public long revision() {
    return revision;
  }

  /** The player's auto-advance switch. */
  public void setAutoAdvance(boolean on) {
    if (progress.autoAdvance() != on) {
      progress.setAutoAdvance(on);
      revision++;
    }
  }

  /** Compiles code with the player's current unlocks and the turret's memory. */
  public CompileResult compile(String source) {
    return data.compiler().compile(source, progress.unlocks(), progress.memoryLines(data));
  }

  /**
   * The wave map (D-49): goes to any wave already reached. A running wave is abandoned without pay
   * or defeat (D-50) and the chosen one waits for {@link #deploy()}.
   *
   * @return false for a wave not reached yet
   */
  public boolean selectWave(int wave) {
    if (!progress.goTo(wave)) {
      return false;
    }
    revision++;
    build(true);
    return true;
  }

  /**
   * Something bought in the shop: a waiting wave is rebuilt with it (a new turret, more damage…); a
   * running one keeps what it started with.
   */
  public void purchased() {
    revision++;
    if (state == State.AWAITING_DEPLOY) {
      build(true);
    }
  }

  /** Speed ×3 only on waves already beaten without leaks (plan, "Farmeo"). */
  public boolean speedUpAllowed() {
    return progress.speedUpAllowed(battle.wave());
  }

  public State state() {
    return state;
  }

  /** The running wave, or the next one built but not started while it waits. */
  public Battle battle() {
    return battle;
  }

  public Progress progress() {
    return progress;
  }

  /** Report of the last finished wave, or {@code null} before the first. */
  public WaveReport lastReport() {
    return lastReport;
  }

  /** What the last finished wave paid and where it led, or {@code null} before the first. */
  public WaveOutcome lastOutcome() {
    return lastOutcome;
  }

  /** Waves finished so far; it changes exactly when {@link #lastOutcome()} does. */
  public int wavesFinished() {
    return wavesFinished;
  }

  private void next() {
    build(!progress.beaten(progress.currentWave()));
  }

  private void build(boolean wait) {
    List<CompiledScript> scripts = new ArrayList<>();
    memoryUsed = 0;
    for (int i = 0; i < progress.turretCount(); i++) {
      CompileResult compiled = compile(sources.get(i));
      scripts.add(compiled.script().orElse(null));
      memoryUsed = Math.max(memoryUsed, compiled.memoryUsed());
    }
    battle =
        new Battle(
            data,
            new BattleSetup(
                progress.currentWave(), scripts, progress.loadout(), 1.0, battlesBuilt++));
    state = wait ? State.AWAITING_DEPLOY : State.RUNNING;
  }
}
