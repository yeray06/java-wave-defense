package io.github.yeray06.wavedefense.sim.save;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Version 1 to 2: money gives way to stars (D-57). The old game is paid as the new rules would have
 * paid it: a star for every wave beaten and one for every perfect wave, and a trophy for every boss
 * killed. Code challenges were not measured, so none is counted. Everything bought is kept.
 */
final class MoneyToStars implements SaveCodec.Migration {
  @Override
  public int from() {
    return 1;
  }

  @Override
  public void apply(ObjectNode save) {
    if (!(save.get("progress") instanceof ObjectNode progress)) {
      throw new IllegalArgumentException("version 1 save without progress");
    }
    progress.remove("moneyCents");
    int stars = size(progress.get("beatenWaves")) + size(progress.get("perfectWaves"));
    progress.put("stars", stars);
    progress.put("trophies", size(progress.get("bossesKilled")));
    progress.putArray("challengeWaves");
  }

  private static int size(JsonNode waves) {
    if (waves == null || !waves.isArray()) {
      throw new IllegalArgumentException("version 1 save without its waves");
    }
    return waves.size();
  }
}
