package io.github.yeray06.wavedefense.game.panel;

import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.sim.progress.Career;

/** A turret's name as the player sees it: theirs (D-63) or «Torreta N». */
public final class TurretNames {
  private TurretNames() {}

  public static String of(Career career, Texts texts, int slot) {
    String name = career.name(slot);
    return name.isEmpty() ? texts.format("editor.turret", slot + 1) : name;
  }
}
