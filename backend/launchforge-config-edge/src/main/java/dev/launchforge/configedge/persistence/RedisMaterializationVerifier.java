package dev.launchforge.configedge.persistence;

import dev.launchforge.contracts.snapshots.RedisMaterializationProvenance;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "launchforge.config-edge",
    name = "redis-enabled",
    havingValue = "true",
    matchIfMissing = true)
final class RedisMaterializationVerifier {
  private static final int MAXIMUM_KEYS = 8;
  private final Map<String, PublicKey> trustedKeys;

  RedisMaterializationVerifier(
      @Value("${launchforge.config-edge.materialization-verification-keys}")
          String verificationKeys) {
    this.trustedKeys = parse(verificationKeys);
  }

  void verifySnapshot(
      UUID environmentId,
      long revision,
      int schemaVersion,
      String checksum,
      String canonicalSnapshot,
      Object provenanceVersion,
      Object provenanceKeyId,
      Object snapshotSignature) {
    PublicKey key = trustedKey(provenanceVersion, provenanceKeyId);
    if (!RedisMaterializationProvenance.verifySnapshot(
        key,
        environmentId,
        revision,
        schemaVersion,
        checksum,
        canonicalSnapshot,
        stringValue(snapshotSignature, "snapshotSignature"))) {
      throw new IllegalArgumentException("Redis snapshot provenance is invalid");
    }
  }

  void verifyRevision(
      UUID environmentId,
      long revision,
      Object provenanceVersion,
      Object provenanceKeyId,
      Object revisionSignature) {
    PublicKey key = trustedKey(provenanceVersion, provenanceKeyId);
    if (!RedisMaterializationProvenance.verifyRevision(
        key, environmentId, revision, stringValue(revisionSignature, "revisionSignature"))) {
      throw new IllegalArgumentException("Redis revision provenance is invalid");
    }
  }

  private PublicKey trustedKey(Object version, Object keyId) {
    int parsedVersion;
    try {
      parsedVersion = Integer.parseInt(stringValue(version, "provenanceVersion"));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Redis provenance version is invalid", exception);
    }
    if (parsedVersion != RedisMaterializationProvenance.VERSION) {
      throw new IllegalArgumentException("Redis provenance version is unsupported");
    }
    String parsedKeyId =
        RedisMaterializationProvenance.requireKeyId(stringValue(keyId, "provenanceKeyId"));
    PublicKey key = trustedKeys.get(parsedKeyId);
    if (key == null) {
      throw new IllegalArgumentException("Redis provenance key is not trusted");
    }
    return key;
  }

  private static Map<String, PublicKey> parse(String verificationKeys) {
    if (verificationKeys == null || verificationKeys.isBlank()) {
      throw new IllegalArgumentException("Materialization verification keys are required");
    }
    Map<String, PublicKey> parsed = new LinkedHashMap<>();
    Arrays.stream(verificationKeys.split(",", -1))
        .map(String::trim)
        .forEach(
            entry -> {
              int separator = entry.indexOf(':');
              if (separator < 1 || separator == entry.length() - 1) {
                throw new IllegalArgumentException("Materialization verification keys are invalid");
              }
              String keyId =
                  RedisMaterializationProvenance.requireKeyId(entry.substring(0, separator));
              PublicKey previous =
                  parsed.putIfAbsent(
                      keyId,
                      RedisMaterializationProvenance.decodePublicKey(
                          entry.substring(separator + 1)));
              if (previous != null) {
                throw new IllegalArgumentException(
                    "Materialization verification key IDs must be unique");
              }
            });
    if (parsed.size() > MAXIMUM_KEYS) {
      throw new IllegalArgumentException("Too many materialization verification keys");
    }
    return Map.copyOf(parsed);
  }

  private static String stringValue(Object value, String field) {
    if (value == null || value.toString().isBlank()) {
      throw new IllegalArgumentException("Redis snapshot is missing " + field);
    }
    return value.toString();
  }
}
