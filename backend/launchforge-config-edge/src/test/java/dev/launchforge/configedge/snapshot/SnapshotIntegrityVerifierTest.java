package dev.launchforge.configedge.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.launchforge.configedge.configuration.ConfigEdgeProperties;
import dev.launchforge.configedge.persistence.EdgeRepository.StoredSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.erdtman.jcs.JsonCanonicalizer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class SnapshotIntegrityVerifierTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void verifiesCanonicalChecksumAndBuildsOpaqueRevisionEtag() throws Exception {
    StoredSnapshot stored = snapshot(7, 1024 * 1024);
    SnapshotIntegrityVerifier verifier = new SnapshotIntegrityVerifier(objectMapper, properties());

    SnapshotIntegrityVerifier.VerifiedSnapshot verified = verifier.verify(stored);

    assertEquals(7, verified.revision());
    assertEquals(stored.checksum(), verified.checksum());
    assertTrue(verified.etag().matches("\"env_[0-9a-f]{16}_rev_7_[0-9a-f]{64}\""));
    assertEquals(stored.canonicalSnapshot(), verified.canonicalJson());
  }

  @Test
  void corruptAndOversizeSnapshotsAreNeverServed() throws Exception {
    StoredSnapshot valid = snapshot(8, 1024 * 1024);
    SnapshotIntegrityVerifier verifier = new SnapshotIntegrityVerifier(objectMapper, properties());
    StoredSnapshot corrupt =
        new StoredSnapshot(
            valid.environmentId(),
            valid.revision(),
            valid.schemaVersion(),
            valid.canonicalSnapshot().replace("\"revision\":8", "\"revision\":9"),
            valid.checksum());
    StoredSnapshot oversize = snapshot(9, 1_100_000);

    assertThrows(SnapshotUnavailableException.class, () -> verifier.verify(corrupt));
    assertThrows(SnapshotUnavailableException.class, () -> verifier.verify(oversize));
  }

  private StoredSnapshot snapshot(long revision, int padding) throws Exception {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("schemaVersion", 1);
    fields.put("algorithmVersion", 1);
    fields.put("projectKey", "storefront");
    fields.put("environmentKey", "development");
    fields.put("revision", revision);
    fields.put("generatedAt", "2026-08-11T12:00:00Z");
    fields.put("flags", Map.of());
    if (padding > 1024 * 1024) {
      fields.put("padding", "x".repeat(padding));
    }
    ObjectNode root = (ObjectNode) objectMapper.valueToTree(fields);
    String withoutChecksum =
        new JsonCanonicalizer(objectMapper.writeValueAsString(root)).getEncodedString();
    String checksum =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(withoutChecksum.getBytes(StandardCharsets.UTF_8)));
    root.put("checksum", checksum);
    String canonical =
        new JsonCanonicalizer(objectMapper.writeValueAsString(root)).getEncodedString();
    return new StoredSnapshot(UUID.randomUUID(), revision, 1, canonical, checksum);
  }

  private static ConfigEdgeProperties properties() {
    return new ConfigEdgeProperties(
        1024 * 1024, Duration.ofSeconds(1), Duration.ofSeconds(15), 10, 2);
  }
}
