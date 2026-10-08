package io.github.yeray06.wavedefense.sim.save;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Version 3 to 4: the career (phase 9b, D-63). Older games had no records, streak, achievements,
 * hats or names, and were never expert games. Achievements already earned unlock on loading.
 */
final class AddCareer implements SaveCodec.Migration {
  @Override
  public int from() {
    return 3;
  }

  @Override
  public void apply(ObjectNode save) {
    if (save.has("career")) {
      // Only a test's pretend old file, written by this game, has one already.
      return;
    }
    ObjectNode career = save.putObject("career");
    career.putObject("records");
    career.put("streak", 0);
    career.put("streakLastWave", 0);
    career.put("streakScore", 0);
    career.put("bestStreak", 0);
    career.put("bestStreakScore", 0);
    career.put("greetings", 0);
    career.putArray("faultsSeen");
    career.putArray("achievements");
    career.putArray("names");
    career.putArray("hats");
    career.put("expert", false);
  }
}
