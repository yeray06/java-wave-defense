package io.github.yeray06.wavedefense.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileStoreTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-07T18:30:00Z"), ZoneOffset.UTC);

  @TempDir Path dir;

  private AtomicFileStore store() {
    return new AtomicFileStore(dir.resolve("sub").resolve("save.json"), CLOCK);
  }

  /** Accepts only texts that start with "ok". */
  private static String strict(String text) {
    if (!text.startsWith("ok")) {
      throw new IllegalArgumentException("not ok: " + text);
    }
    return text;
  }

  @Test
  void writesCreatingTheFolderAndKeepsThePreviousVersionAsBackup() throws IOException {
    AtomicFileStore store = store();
    store.write("ok 1");
    assertEquals("ok 1", Files.readString(store.file()));
    assertFalse(Files.exists(store.backup()), "nothing to back up the first time");
    store.write("ok 2");
    assertEquals("ok 2", Files.readString(store.file()));
    assertEquals("ok 1", Files.readString(store.backup()));
    assertFalse(Files.exists(dir.resolve("sub").resolve("save.json.tmp")));

    var loaded = store.read(AtomicFileStoreTest::strict);
    assertEquals(Optional.of("ok 2"), loaded.value());
    assertEquals(AtomicFileStore.Source.MAIN, loaded.source());
    assertTrue(loaded.setAside().isEmpty());
  }

  @Test
  void aMissingFileReadsAsNothing() {
    var loaded = store().read(AtomicFileStoreTest::strict);
    assertEquals(Optional.empty(), loaded.value());
    assertEquals(AtomicFileStore.Source.NONE, loaded.source());
  }

  @Test
  void aBrokenFileFallsBackToTheBackupAndIsKeptAside() throws IOException {
    AtomicFileStore store = store();
    store.write("ok 1");
    store.write("ok 2");
    Files.writeString(store.file(), "{ half a fi");
    var loaded = store.read(AtomicFileStoreTest::strict);
    assertEquals(Optional.of("ok 1"), loaded.value());
    assertEquals(AtomicFileStore.Source.BACKUP, loaded.source());
    Path aside = dir.resolve("sub").resolve("save.json.corrupt-20261007-183000");
    assertEquals(java.util.List.of(aside), loaded.setAside());
    assertEquals("{ half a fi", Files.readString(aside));
    assertEquals(1, loaded.problems().size());
    assertFalse(Files.exists(store.file()));
  }

  @Test
  void whenBothAreBrokenThereIsNothingAndBothAreKept() throws IOException {
    AtomicFileStore store = store();
    store.write("bad 1");
    store.write("bad 2");
    var loaded = store.read(AtomicFileStoreTest::strict);
    assertEquals(Optional.empty(), loaded.value());
    assertEquals(AtomicFileStore.Source.NONE, loaded.source());
    assertEquals(2, loaded.setAside().size());
    for (Path path : loaded.setAside()) {
      assertTrue(Files.exists(path), path.toString());
    }
  }

  @Test
  void aLeftoverTemporaryFileFromACrashIsIgnored() throws IOException {
    AtomicFileStore store = store();
    store.write("ok 1");
    Files.writeString(dir.resolve("sub").resolve("save.json.tmp"), "ok half");
    assertEquals(Optional.of("ok 1"), store.read(AtomicFileStoreTest::strict).value());
    store.write("ok 2");
    assertEquals("ok 2", Files.readString(store.file()));
  }

  @Test
  void deleteRemovesTheFileAndItsBackup() throws IOException {
    AtomicFileStore store = store();
    store.write("ok 1");
    store.write("ok 2");
    store.delete();
    assertFalse(Files.exists(store.file()));
    assertFalse(Files.exists(store.backup()));
  }
}
