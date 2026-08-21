package dev.launchforge.contracts.snapshots;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/** Authenticates Redis materializations without giving Config Edge the signing credential. */
public final class RedisMaterializationProvenance {
  public static final int VERSION = 1;
  private static final String ALGORITHM = "Ed25519";
  private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,32}");
  private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
  private static final Pattern SIGNATURE = Pattern.compile("[A-Za-z0-9_-]{86}");
  private static final byte[] SNAPSHOT_DOMAIN =
      "LaunchForge.RedisSnapshot.v1".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] REVISION_DOMAIN =
      "LaunchForge.RedisRevision.v1".getBytes(StandardCharsets.US_ASCII);

  private RedisMaterializationProvenance() {}

  public static PrivateKey decodePrivateKey(String encoded) {
    byte[] bytes = decodeKey(encoded, "private key");
    try {
      return keyFactory().generatePrivate(new PKCS8EncodedKeySpec(bytes));
    } catch (InvalidKeySpecException exception) {
      throw new IllegalArgumentException("Materialization private key is invalid", exception);
    }
  }

  public static PublicKey decodePublicKey(String encoded) {
    byte[] bytes = decodeKey(encoded, "public key");
    try {
      return keyFactory().generatePublic(new X509EncodedKeySpec(bytes));
    } catch (InvalidKeySpecException exception) {
      throw new IllegalArgumentException("Materialization public key is invalid", exception);
    }
  }

  public static String signSnapshot(
      PrivateKey key,
      UUID environmentId,
      long revision,
      int schemaVersion,
      String checksum,
      String canonicalSnapshot) {
    return sign(
        key, snapshotPayload(environmentId, revision, schemaVersion, checksum, canonicalSnapshot));
  }

  public static boolean verifySnapshot(
      PublicKey key,
      UUID environmentId,
      long revision,
      int schemaVersion,
      String checksum,
      String canonicalSnapshot,
      String encodedSignature) {
    return verify(
        key,
        snapshotPayload(environmentId, revision, schemaVersion, checksum, canonicalSnapshot),
        encodedSignature);
  }

  public static String signRevision(PrivateKey key, UUID environmentId, long revision) {
    return sign(key, revisionPayload(environmentId, revision));
  }

  public static boolean verifyRevision(
      PublicKey key, UUID environmentId, long revision, String encodedSignature) {
    return verify(key, revisionPayload(environmentId, revision), encodedSignature);
  }

  public static String requireKeyId(String keyId) {
    if (keyId == null || !KEY_ID.matcher(keyId).matches()) {
      throw new IllegalArgumentException("Materialization key ID is invalid");
    }
    return keyId;
  }

  private static byte[] snapshotPayload(
      UUID environmentId,
      long revision,
      int schemaVersion,
      String checksum,
      String canonicalSnapshot) {
    if (schemaVersion < 1 || checksum == null || !CHECKSUM.matcher(checksum).matches()) {
      throw new IllegalArgumentException("Materialization snapshot metadata is invalid");
    }
    byte[] checksumBytes = checksum.getBytes(StandardCharsets.US_ASCII);
    byte[] snapshotBytes =
        canonicalSnapshot == null
            ? new byte[0]
            : canonicalSnapshot.getBytes(StandardCharsets.UTF_8);
    if (snapshotBytes.length == 0) {
      throw new IllegalArgumentException("Materialization snapshot is empty");
    }
    return payload(
        SNAPSHOT_DOMAIN,
        environmentId,
        revision,
        output -> {
          output.writeInt(schemaVersion);
          writeBytes(output, checksumBytes);
          writeBytes(output, snapshotBytes);
        });
  }

  private static byte[] revisionPayload(UUID environmentId, long revision) {
    return payload(REVISION_DOMAIN, environmentId, revision, output -> {});
  }

  private static byte[] payload(
      byte[] domain, UUID environmentId, long revision, PayloadWriter writer) {
    if (environmentId == null || revision < 1) {
      throw new IllegalArgumentException("Materialization identity is invalid");
    }
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        writeBytes(output, domain);
        output.writeInt(VERSION);
        output.writeLong(environmentId.getMostSignificantBits());
        output.writeLong(environmentId.getLeastSignificantBits());
        output.writeLong(revision);
        writer.write(output);
      }
      return bytes.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to encode materialization provenance", exception);
    }
  }

  private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
    output.writeInt(value.length);
    output.write(value);
  }

  private static String sign(PrivateKey key, byte[] payload) {
    if (key == null) {
      throw new IllegalArgumentException("Materialization signing key is required");
    }
    try {
      Signature signer = Signature.getInstance(ALGORITHM);
      signer.initSign(key);
      signer.update(payload);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Java runtime does not provide Ed25519", exception);
    } catch (InvalidKeyException | SignatureException exception) {
      throw new IllegalArgumentException("Materialization signing key is invalid", exception);
    }
  }

  private static boolean verify(PublicKey key, byte[] payload, String encodedSignature) {
    if (key == null || encodedSignature == null || !SIGNATURE.matcher(encodedSignature).matches()) {
      return false;
    }
    try {
      Signature verifier = Signature.getInstance(ALGORITHM);
      verifier.initVerify(key);
      verifier.update(payload);
      return verifier.verify(Base64.getUrlDecoder().decode(encodedSignature));
    } catch (IllegalArgumentException | InvalidKeyException | SignatureException exception) {
      return false;
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Java runtime does not provide Ed25519", exception);
    }
  }

  private static byte[] decodeKey(String encoded, String name) {
    if (encoded == null || encoded.isBlank() || encoded.length() > 256) {
      throw new IllegalArgumentException("Materialization " + name + " is invalid");
    }
    try {
      return Base64.getUrlDecoder().decode(encoded);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Materialization " + name + " is invalid", exception);
    }
  }

  private static KeyFactory keyFactory() {
    try {
      return KeyFactory.getInstance(ALGORITHM);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Java runtime does not provide Ed25519", exception);
    }
  }

  @FunctionalInterface
  private interface PayloadWriter {
    void write(DataOutputStream output) throws IOException;
  }
}
