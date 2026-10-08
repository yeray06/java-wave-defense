package io.github.yeray06.wavedefense.sim.progress;

import io.github.yeray06.wavedefense.script.CompileResult;
import io.github.yeray06.wavedefense.script.LibraryResult;
import io.github.yeray06.wavedefense.script.check.DebugVar;
import io.github.yeray06.wavedefense.script.check.Library;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.world.Battle;
import io.github.yeray06.wavedefense.sim.world.BattleSetup;
import io.github.yeray06.wavedefense.sim.world.WaveReport;
import java.util.ArrayList;
import java.util.BitSet;
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
  private final Career career;
  private final Progression progression;
  private final List<String> sources;
  private final List<String> drafts;
  private String librarySource = "";
  private String libraryDraft = "";
  private Library library = Library.EMPTY;
  private Battle battle;
  private State state;
  private WaveReport lastReport;
  private WaveOutcome lastOutcome;
  private CareerOutcome lastCareer;
  private long trials;
  private int wavesFinished;
  private int purchasesUnlocking;
  private long battlesBuilt;
  private long revision;

  /** Debugger breakpoints (1-based lines) of each slot's script and of the library; not saved. */
  private final List<BitSet> breakpoints;

  private final BitSet libraryBreakpoints = new BitSet();

  /** The variables of each turret's script in the battle built, and of its library. */
  private List<List<DebugVar>> battleVars = List.of();

  private List<DebugVar> battleLibraryVars = List.of();

  /** The turret whose script stopped at a breakpoint, with the wave paused; -1 when running. */
  private int debugTurret = -1;

  /** The most memory any turret's code takes in the battle built: for the code challenge. */
  private int memoryUsed;

  /** Starts from {@code progress}; every turret runs the data's initial script (D-14). */
  public WaveLoop(GameData data, Progress progress) {
    this(data, progress, Career.newGame(data, false));
  }

  /** Starts from {@code progress} and a career; every turret runs the initial script (D-14). */
  public WaveLoop(GameData data, Progress progress, Career career) {
    this.data = data;
    this.progress = progress;
    this.career = career;
    this.progression = new Progression(data);
    int slots = data.map().turretSlots().size();
    this.sources = new ArrayList<>(Collections.nCopies(slots, data.turret().initialScript()));
    this.drafts = new ArrayList<>(sources);
    this.breakpoints = new ArrayList<>();
    for (int i = 0; i < slots; i++) {
      breakpoints.add(new BitSet());
    }
    next();
  }

  /**
   * The player's code, as saved.
   *
   * @param scripts what each turret runs
   * @param drafts what the editor held for each turret
   * @param library the library that runs
   * @param libraryDraft what the editor held for the library
   */
  public record Code(
      List<String> scripts, List<String> drafts, String library, String libraryDraft) {
    public Code {
      scripts = List.copyOf(scripts);
      drafts = List.copyOf(drafts);
    }
  }

  /**
   * A saved game: its progress, the code each turret runs and what the editor held. The current
   * wave waits for {@link #deploy()} even if it was beaten, so loading never starts a wave by
   * itself. Saved code that no longer compiles (the data changed) falls back: the library to empty,
   * a script to the initial one; its draft is kept so the player sees their code.
   */
  public static WaveLoop restore(
      GameData data, Progress progress, Career career, Code code, long battles) {
    WaveLoop loop = new WaveLoop(data, progress, career);
    LibraryResult library = loop.compileLibrary(code.library());
    if (library.succeeded()) {
      loop.librarySource = code.library();
      loop.library = library.library();
    }
    loop.libraryDraft = code.libraryDraft();
    List<String> scripts = code.scripts();
    List<String> drafts = code.drafts();
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
    if (state != State.RUNNING || debugTurret >= 0) {
      return;
    }
    battle.tick();
    debugTurret = battle.takeDebugStop();
    if (battle.finished()) {
      debugTurret = -1;
      lastReport = battle.report();
      lastOutcome = progression.apply(progress, lastReport, memoryUsed);
      lastCareer = career.apply(progress, lastReport, memoryUsed);
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

  /**
   * The player's library: methods every turret can call. If it compiles it replaces the running
   * one, and a waiting wave is rebuilt with it; turret scripts that call a method it no longer has
   * stop compiling, and their turrets stay idle until fixed. If it does not compile, the last one
   * that did keeps running.
   *
   * @return the compilation, for the editor's errors
   */
  public LibraryResult setLibrary(String source) {
    if (!source.equals(libraryDraft)) {
      libraryDraft = source;
      revision++;
    }
    LibraryResult result = compileLibrary(source);
    if (result.succeeded() && !source.equals(librarySource)) {
      librarySource = source;
      library = result.library();
      revision++;
      if (state == State.AWAITING_DEPLOY) {
        build(true);
      }
    }
    return result;
  }

  // ------------------------------------------------------------------ debugger

  /**
   * The player's breakpoints on a slot's script (1-based lines). When a turret's cycle reaches one,
   * the wave pauses after that tick and {@link #debugTurret()} says whose recording to show.
   */
  public void setBreakpoints(int slot, BitSet lines) {
    breakpoints.get(slot).clear();
    breakpoints.get(slot).or(lines);
    applyBreakpoints();
  }

  /** Breakpoints on the library's lines; they stop any turret that runs them. */
  public void setLibraryBreakpoints(BitSet lines) {
    libraryBreakpoints.clear();
    libraryBreakpoints.or(lines);
    applyBreakpoints();
  }

  /** A slot's breakpoints, as set. */
  public BitSet breakpoints(int slot) {
    return (BitSet) breakpoints.get(slot).clone();
  }

  public BitSet libraryBreakpoints() {
    return (BitSet) libraryBreakpoints.clone();
  }

  /**
   * «Paso a paso»: the turret's next cycle is recorded and the wave pauses after it. Resumes a
   * paused wave first, so pressing it again walks cycle by cycle.
   */
  public void stepCycle(int turret) {
    if (turret < battle.turretCount()) {
      debugTurret = -1;
      battle.breakOnNextCycle(turret);
    }
  }

  /** The turret whose recording the debugger shows while the wave is paused, or -1. */
  public int debugTurret() {
    return debugTurret;
  }

  /** The variables of a turret's script as the battle runs it, for the debugger. */
  public List<DebugVar> debugVars(int turret) {
    return turret < battleVars.size() ? battleVars.get(turret) : List.of();
  }

  /** The variables of the library's methods as the battle runs them. */
  public List<DebugVar> libraryDebugVars() {
    return battleLibraryVars;
  }

  /** Whether the wave is paused by the debugger. */
  public boolean debugPaused() {
    return debugTurret >= 0;
  }

  /** The debugger's «Continuar»: the wave runs again until the next breakpoint. */
  public void resumeDebug() {
    debugTurret = -1;
  }

  private void applyBreakpoints() {
    for (int i = 0; i < battle.turretCount(); i++) {
      battle.setBreakpoints(i, breakpoints.get(i), libraryBreakpoints);
    }
  }

  /** The library's code that runs: the last that compiled. */
  public String library() {
    return librarySource;
  }

  /** What the editor holds for the library, even broken. */
  public String libraryDraft() {
    return libraryDraft;
  }

  /** Compiles a library with the player's current unlocks. */
  public LibraryResult compileLibrary(String source) {
    return data.compiler().compileLibrary(source, progress.unlocks());
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

  /** Compiles a turret's code with the library, the player's unlocks and the turret's memory. */
  public CompileResult compile(String source) {
    return data.compiler().compile(source, library, progress.unlocks(), progress.memoryLines(data));
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
    List<String> unlocked = career.unlock(progress);
    if (!unlocked.isEmpty()) {
      lastCareer = new CareerOutcome(java.util.Set.of(), career.streak(), 0, unlocked);
      purchasesUnlocking++;
    }
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

  /**
   * What the last finished wave (or purchase) added to the career, or {@code null} before any.
   * Changes with {@link #careerChanges()}.
   */
  public CareerOutcome lastCareer() {
    return lastCareer;
  }

  /** Grows whenever {@link #lastCareer()} changes: to show new achievements once. */
  public long careerChanges() {
    return wavesFinished + purchasesUnlocking;
  }

  public Career career() {
    return career;
  }

  /** Names a turret slot (D-63). */
  public String setTurretName(int slot, String name) {
    String kept = career.setName(slot, name);
    revision++;
    return kept;
  }

  /** Puts an unlocked hat on a turret slot, or takes it off with an empty id (D-63). */
  public boolean setTurretHat(int slot, String hat) {
    boolean done = career.setHat(slot, hat);
    if (done) {
      revision++;
    }
    return done;
  }

  /**
   * «Probar» (D-65): plays a wave already reached from start to end at once, with the code the
   * turrets run now, and returns its report. Nothing is paid or recorded and the loop does not
   * change. The player is taken to reload by hand as soon as a turret is empty. Each trial has its
   * own seed, so it is a sample of how the wave goes, not a forecast of the next real run.
   *
   * @return {@code null} for a wave not reached yet
   */
  public WaveReport trial(int wave) {
    if (wave < 1 || wave > progress.highestWaveReached()) {
      return null;
    }
    List<CompiledScript> scripts = new ArrayList<>();
    for (int i = 0; i < progress.turretCount(); i++) {
      scripts.add(compile(sources.get(i)).script().orElse(null));
    }
    // Negative seeds never meet a real battle's, which count up from zero.
    Battle trial =
        new Battle(data, new BattleSetup(wave, scripts, progress.loadout(), 1.0, -1 - trials++));
    while (!trial.finished()) {
      trial.tick();
      for (int t = 0; t < trial.turretCount(); t++) {
        if (trial.turret(t).ammo() == 0 && !trial.turret(t).reloading()) {
          trial.manualReload(t);
        }
      }
    }
    return trial.report();
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
    List<List<DebugVar>> vars = new ArrayList<>();
    memoryUsed = 0;
    for (int i = 0; i < progress.turretCount(); i++) {
      CompileResult compiled = compile(sources.get(i));
      scripts.add(compiled.script().orElse(null));
      vars.add(compiled.debugVars());
      memoryUsed = Math.max(memoryUsed, compiled.memoryUsed());
    }
    battle =
        new Battle(
            data,
            new BattleSetup(
                progress.currentWave(), scripts, progress.loadout(), 1.0, battlesBuilt++));
    state = wait ? State.AWAITING_DEPLOY : State.RUNNING;
    battleVars = List.copyOf(vars);
    battleLibraryVars = library.debugVars();
    debugTurret = -1;
    applyBreakpoints();
  }
}
