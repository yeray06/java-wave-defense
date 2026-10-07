package io.github.yeray06.wavedefense.game.save;

import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import java.util.function.Predicate;

/**
 * Saves the game by itself (D-55): whenever {@link WaveLoop#revision()} moves (code, purchases, a
 * finished wave…). After a quiet moment a change is saved at once; while the player keeps typing,
 * at most once per interval. Closing the game saves at once. Writing a few KB takes milliseconds,
 * so it runs on the game thread.
 */
public final class AutoSaver {
  private final Predicate<WaveLoop> save;
  private final float intervalSeconds;
  private WaveLoop loop;
  private long savedRevision;
  private float sinceSave;

  /**
   * @param save writes the game; false when it failed
   */
  public AutoSaver(Predicate<WaveLoop> save, float intervalSeconds) {
    this.save = save;
    this.intervalSeconds = intervalSeconds;
  }

  /** Follows {@code loop}, taking its state as already saved. */
  public void track(WaveLoop loop) {
    this.loop = loop;
    this.savedRevision = loop.revision();
    this.sinceSave = 0;
  }

  /**
   * Saves if something changed and the interval has passed.
   *
   * @return false only when a save was tried and failed
   */
  public boolean update(float delta) {
    sinceSave += delta;
    if (sinceSave < intervalSeconds) {
      return true;
    }
    return saveIfChanged();
  }

  /** Saves now if anything changed: the game is closing or going to the background. */
  public boolean saveIfChanged() {
    if (loop == null || loop.revision() == savedRevision) {
      return true;
    }
    long revision = loop.revision();
    sinceSave = 0;
    if (!save.test(loop)) {
      return false;
    }
    savedRevision = revision;
    return true;
  }
}
