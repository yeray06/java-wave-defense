package io.github.yeray06.wavedefense.platform;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * One text file that survives crashes and power cuts (ARCHITECTURE §7): it is written to a
 * temporary file, flushed to disk, the previous version is kept as {@code .bak} and the new one
 * takes its place in a single rename. Reading falls back to the backup, and a file that cannot be
 * read is kept aside as {@code .corrupt-<date>} instead of being lost.
 */
public final class AtomicFileStore {
  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  /** Where a read came from. */
  public enum Source {
    MAIN,
    BACKUP,
    NONE
  }

  /**
   * @param value what was read, empty if neither file could be used
   * @param source which file it came from
   * @param setAside files that could not be read, renamed so the next write does not lose them
   * @param problems why each of them could not be read, for the log
   */
  public record Loaded<T>(
      Optional<T> value, Source source, List<Path> setAside, List<String> problems) {
    public Loaded {
      setAside = List.copyOf(setAside);
      problems = List.copyOf(problems);
    }
  }

  private final Path file;
  private final Path backup;
  private final Path temporary;
  private final Clock clock;

  /**
   * @param clock only names the copies of unreadable files
   */
  public AtomicFileStore(Path file, Clock clock) {
    this.file = file;
    this.backup = sibling(".bak");
    this.temporary = sibling(".tmp");
    this.clock = clock;
  }

  public Path file() {
    return file;
  }

  public Path backup() {
    return backup;
  }

  /** Replaces the file's content; the previous content becomes the backup. */
  public void write(String content) throws IOException {
    Path parent = file.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    ByteBuffer bytes = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
    try (FileChannel channel =
        FileChannel.open(
            temporary,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING)) {
      while (bytes.hasRemaining()) {
        channel.write(bytes);
      }
      channel.force(true);
    }
    if (Files.exists(file)) {
      Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
    }
    try {
      Files.move(
          temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /**
   * Reads the file, or its backup if the file is missing or {@code parse} rejects it. Never throws
   * for a bad file: {@code parse} may throw any {@link RuntimeException} to reject one.
   */
  public <T> Loaded<T> read(Function<String, T> parse) {
    List<Path> setAside = new ArrayList<>();
    List<String> problems = new ArrayList<>();
    Optional<T> main = tryRead(file, parse, setAside, problems);
    if (main.isPresent()) {
      return new Loaded<>(main, Source.MAIN, setAside, problems);
    }
    Optional<T> fromBackup = tryRead(backup, parse, setAside, problems);
    if (fromBackup.isPresent()) {
      return new Loaded<>(fromBackup, Source.BACKUP, setAside, problems);
    }
    return new Loaded<>(Optional.empty(), Source.NONE, setAside, problems);
  }

  /** Removes the file and its backup. */
  public void delete() throws IOException {
    Files.deleteIfExists(file);
    Files.deleteIfExists(backup);
    Files.deleteIfExists(temporary);
  }

  private <T> Optional<T> tryRead(
      Path path, Function<String, T> parse, List<Path> setAside, List<String> problems) {
    if (!Files.isRegularFile(path)) {
      return Optional.empty();
    }
    try {
      return Optional.of(parse.apply(Files.readString(path, StandardCharsets.UTF_8)));
    } catch (IOException | RuntimeException e) {
      problems.add(path.getFileName() + ": " + e);
      Path copy =
          path.resolveSibling(
              path.getFileName() + ".corrupt-" + LocalDateTime.now(clock).format(STAMP));
      try {
        Files.move(path, copy, StandardCopyOption.REPLACE_EXISTING);
        setAside.add(copy);
      } catch (IOException moveFailed) {
        // Left where it is: the next write replaces it, which is still better than crashing.
        problems.add(path.getFileName() + " could not be set aside: " + moveFailed);
      }
      return Optional.empty();
    }
  }

  private Path sibling(String suffix) {
    return file.resolveSibling(file.getFileName() + suffix);
  }
}
