package dev.launchforge.configedge.snapshot;

import dev.launchforge.configedge.configuration.ConfigEdgeProperties;
import dev.launchforge.configedge.persistence.EdgeRepository.StoredSnapshot;
import dev.launchforge.configedge.snapshot.SnapshotIntegrityVerifier.VerifiedSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Produces a separate immutable browser representation before checksum/ETag calculation. */
@Component
public final class BrowserSnapshotProjector {
  private final ObjectMapper objectMapper;
  private final SnapshotIntegrityVerifier integrityVerifier;
  private final int maximumSnapshotBytes;

  public BrowserSnapshotProjector(
      ObjectMapper objectMapper,
      SnapshotIntegrityVerifier integrityVerifier,
      ConfigEdgeProperties properties) {
    this.objectMapper = objectMapper;
    this.integrityVerifier = integrityVerifier;
    this.maximumSnapshotBytes = properties.maximumSnapshotBytes();
  }

  public VerifiedSnapshot project(StoredSnapshot stored) {
    VerifiedSnapshot authoritative = integrityVerifier.verify(stored);
    try {
      JsonNode parsed = objectMapper.readTree(authoritative.canonicalJson());
      if (!(parsed instanceof ObjectNode root)
          || !(root.get("flags") instanceof ObjectNode flags)) {
        throw new SnapshotUnavailableException();
      }
      for (String flagKey : new ArrayList<>(flags.propertyNames())) {
        JsonNode flag = flags.get(flagKey);
        if (!(flag instanceof ObjectNode) || !flag.path("clientVisible").booleanValue()) {
          flags.remove(flagKey);
        }
      }
      root.remove("checksum");
      String projection =
          new JsonCanonicalizer(objectMapper.writeValueAsString(root)).getEncodedString();
      String checksum = sha256(projection);
      root.put("checksum", checksum);
      String canonical =
          new JsonCanonicalizer(objectMapper.writeValueAsString(root)).getEncodedString();
      if (canonical.getBytes(StandardCharsets.UTF_8).length > maximumSnapshotBytes) {
        throw new SnapshotUnavailableException();
      }
      return new VerifiedSnapshot(
          canonical, stored.revision(), stored.schemaVersion(), checksum, etag(stored, checksum));
    } catch (IOException | RuntimeException exception) {
      if (exception instanceof SnapshotUnavailableException unavailable) {
        throw unavailable;
      }
      throw new SnapshotUnavailableException(exception);
    }
  }

  private static String etag(StoredSnapshot snapshot, String checksum) {
    String environment = sha256(snapshot.environmentId().toString()).substring(0, 16);
    return "\"client_env_" + environment + "_rev_" + snapshot.revision() + '_' + checksum + '\"';
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
}
