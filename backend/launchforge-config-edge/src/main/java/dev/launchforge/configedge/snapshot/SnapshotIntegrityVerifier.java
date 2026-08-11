package dev.launchforge.configedge.snapshot;

import dev.launchforge.configedge.configuration.ConfigEdgeProperties;
import dev.launchforge.configedge.persistence.EdgeRepository.StoredSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public final class SnapshotIntegrityVerifier {
  private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
  private final ObjectMapper objectMapper;
  private final int maximumSnapshotBytes;

  public SnapshotIntegrityVerifier(ObjectMapper objectMapper, ConfigEdgeProperties properties) {
    this.objectMapper = objectMapper;
    this.maximumSnapshotBytes = properties.maximumSnapshotBytes();
  }

  public VerifiedSnapshot verify(StoredSnapshot stored) {
    String canonical = stored.canonicalSnapshot();
    if (canonical.getBytes(StandardCharsets.UTF_8).length > maximumSnapshotBytes) {
      throw new SnapshotUnavailableException();
    }
    try {
      JsonNode parsed = objectMapper.readTree(canonical);
      if (!(parsed instanceof ObjectNode root)
          || stored.schemaVersion() != 1
          || root.path("schemaVersion").intValue() != stored.schemaVersion()
          || root.path("revision").longValue() != stored.revision()
          || !CHECKSUM.matcher(stored.checksum()).matches()
          || !stored.checksum().equals(root.path("checksum").stringValue())
          || !canonical.equals(new JsonCanonicalizer(canonical).getEncodedString())) {
        throw new SnapshotUnavailableException();
      }
      ObjectNode withoutChecksum = root.deepCopy();
      withoutChecksum.remove("checksum");
      String canonicalWithoutChecksum =
          new JsonCanonicalizer(objectMapper.writeValueAsString(withoutChecksum))
              .getEncodedString();
      if (!stored.checksum().equals(sha256(canonicalWithoutChecksum))) {
        throw new SnapshotUnavailableException();
      }
      return new VerifiedSnapshot(
          canonical, stored.revision(), stored.schemaVersion(), stored.checksum(), etag(stored));
    } catch (IOException | RuntimeException exception) {
      if (exception instanceof SnapshotUnavailableException unavailable) {
        throw unavailable;
      }
      throw new SnapshotUnavailableException(exception);
    }
  }

  private static String etag(StoredSnapshot snapshot) {
    String environment = sha256(snapshot.environmentId().toString()).substring(0, 16);
    return "\"env_" + environment + "_rev_" + snapshot.revision() + '_' + snapshot.checksum() + '"';
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
    }
  }

  public record VerifiedSnapshot(
      String canonicalJson, long revision, int schemaVersion, String checksum, String etag) {}
}
