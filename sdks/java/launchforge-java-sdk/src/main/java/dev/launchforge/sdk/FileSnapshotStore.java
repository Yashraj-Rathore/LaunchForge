package dev.launchforge.sdk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Optional;
import java.util.Set;

/** Best-effort durable snapshot storage. Validation remains the responsibility of the parser. */
final class FileSnapshotStore {
  private static final Set<PosixFilePermission> OWNER_ONLY =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  private final Path path;

  FileSnapshotStore(Path path) {
    this.path = path.toAbsolutePath().normalize();
  }

  Optional<byte[]> load() {
    try {
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(path)
          || Files.size(path) > SnapshotParser.MAX_SNAPSHOT_BYTES) {
        return Optional.empty();
      }
      byte[] bytes = Files.readAllBytes(path);
      return bytes.length == 0 ? Optional.empty() : Optional.of(bytes);
    } catch (IOException | SecurityException exception) {
      return Optional.empty();
    }
  }

  boolean save(byte[] bytes) {
    Path parent = path.getParent();
    if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    Path temporary = null;
    try {
      if (Files.isSymbolicLink(path)) {
        return false;
      }
      temporary = Files.createTempFile(parent, ".launchforge-lkg-", ".tmp");
      restrictPermissions(temporary);
      try (FileChannel channel =
          FileChannel.open(
              temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
      }
      Files.move(
          temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      restrictPermissions(path);
      return true;
    } catch (AtomicMoveNotSupportedException exception) {
      return false;
    } catch (IOException | SecurityException exception) {
      return false;
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException | SecurityException ignored) {
          // A failed cleanup cannot invalidate the already active in-memory snapshot.
        }
      }
    }
  }

  private static void restrictPermissions(Path target) throws IOException {
    try {
      Files.setPosixFilePermissions(target, OWNER_ONLY);
    } catch (UnsupportedOperationException ignored) {
      // Windows and other non-POSIX filesystems require operator-managed ACLs.
    }
  }
}
