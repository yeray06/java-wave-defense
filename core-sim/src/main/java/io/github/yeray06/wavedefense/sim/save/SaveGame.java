package io.github.yeray06.wavedefense.sim.save;

import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import java.util.List;

/**
 * A saved game (ARCHITECTURE §7): the progress, the code each turret runs and the editor's drafts
 * (D-55). A wave in progress is not saved: loading waits on the current wave's «Desplegar».
 *
 * @param version the format, for {@link SaveCodec}'s migrations
 * @param battlesBuilt the seed counter, so a loaded game replays as it would have
 */
public record SaveGame(
    int version,
    Progress.Snapshot progress,
    List<String> scripts,
    List<String> drafts,
    long battlesBuilt) {

  public SaveGame {
    if (progress == null || scripts == null || drafts == null) {
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
        loop.battlesBuilt());
  }

  /**
   * The game this save describes.
   *
   * @throws IllegalArgumentException if it does not fit the data
   */
  public WaveLoop restore(GameData data) {
    return WaveLoop.restore(data, Progress.restore(data, progress), scripts, drafts, battlesBuilt);
  }
}
