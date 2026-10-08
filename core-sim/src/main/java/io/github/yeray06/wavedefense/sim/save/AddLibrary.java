package io.github.yeray06.wavedefense.sim.save;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Version 2 to 3: the library of methods (phase 9). Older games had none, so it starts empty, both
 * the code that runs and what the editor holds.
 */
final class AddLibrary implements SaveCodec.Migration {
  @Override
  public int from() {
    return 2;
  }

  @Override
  public void apply(ObjectNode save) {
    save.put("library", "");
    save.put("libraryDraft", "");
  }
}
