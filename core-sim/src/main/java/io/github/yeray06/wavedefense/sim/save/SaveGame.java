package io.github.yeray06.wavedefense.sim.save;

import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import java.util.List;

/**
 * A saved game (ARCHITECTURE §7): the progress, the code each turret runs, the library and the
 * editor's drafts (D-55). A wave in progress is not saved: loading waits on the current wave's
 * «Desplegar».
 *
 * @param version the format, for {@link SaveCodec}'s migrations
 * @param library the library's code that runs: the last that compiled
 * @param libraryDraft what the library's tab holds, even broken
 * @param battlesBuilt the seed counter, so a loaded game replays as it would have
 */
public record SaveGame(
    int version,
    Progress.Snapshot progress,
    List<String> scripts,
    List<String> drafts,
    String library,
    String libraryDraft,
    long battlesBuilt) {

  public SaveGame {
    if (progress == null
        || scripts == null
        || drafts == null
        || library == null
        || libraryDraft == null) {
      throw new IllegalArgumentException("incomplete save");
    }
    if (battlesBuilt < 0) {
      throw new IllegalArgumentException("negative battle count");
    }
    scripts = List.copyOf(scripts);
    drafts = List.copyOf(drafts);
  }

  /** What to save of a game being played. */
  public static SaveGame of(WaveLoop loop) {
    return new SaveGame(
        SaveCodec.VERSION,
        loop.progress().snapshot(),
        loop.scripts(),
        loop.drafts(),
        loop.library(),
        loop.libraryDraft(),
        loop.battlesBuilt());
  }

  /**
   * The game this save describes.
   *
   * @throws IllegalArgumentException if it does not fit the data
   */
  public WaveLoop restore(GameData data) {
    return WaveLoop.restore(
        data,
        Progress.restore(data, progress),
        new WaveLoop.Code(scripts, drafts, library, libraryDraft),
        battlesBuilt);
  }
}
