package dev.launchforge.eventworker.projection;

import dev.launchforge.eventworker.configuration.DistributionProperties;
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
public class SnapshotValidator {
  private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
  private final ObjectMapper objectMapper;
  private final int maximumSnapshotBytes;

  public SnapshotValidator(ObjectMapper objectMapper, DistributionProperties properties) {
    this.objectMapper = objectMapper;
    this.maximumSnapshotBytes = properties.maximumSnapshotBytes();
  }

  public void validate(AuthoritativeSnapshot snapshot) {
    String canonical = snapshot.canonicalSnapshot();
    if (canonical.getBytes(StandardCharsets.UTF_8).length > maximumSnapshotBytes) {
      throw new IllegalArgumentException("Snapshot exceeds the configured size limit");
    }
    try {
      JsonNode parsed = objectMapper.readTree(canonical);
      if (!(parsed instanceof ObjectNode root)
          || snapshot.schemaVersion() != 1
          || root.path("schemaVersion").intValue() != snapshot.schemaVersion()
          || root.path("revision").longValue() != snapshot.revision()
          || !CHECKSUM.matcher(snapshot.checksum()).matches()
          || !snapshot.checksum().equals(root.path("checksum").stringValue())
          || !canonical.equals(new JsonCanonicalizer(canonical).getEncodedString())) {
        throw new IllegalArgumentException("Stored snapshot integrity check failed");
      }
      ObjectNode withoutChecksum = root.deepCopy();
      withoutChecksum.remove("checksum");
      String canonicalWithoutChecksum =
          new JsonCanonicalizer(objectMapper.writeValueAsString(withoutChecksum))
              .getEncodedString();
      if (!snapshot.checksum().equals(sha256(canonicalWithoutChecksum))) {
        throw new IllegalArgumentException("Stored snapshot checksum does not match its content");
      }
    } catch (IOException exception) {
      throw new IllegalArgumentException("Stored snapshot is invalid", exception);
    }
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
