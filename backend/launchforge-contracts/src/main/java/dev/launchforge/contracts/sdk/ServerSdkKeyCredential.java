package dev.launchforge.contracts.sdk;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Version-1 server SDK credential formatting and HMAC verification. */
public final class ServerSdkKeyCredential {
  public static final String PREFIX = "lf_srv_";
  private static final int LOOKUP_BYTES = 12;
  private static final int SECRET_BYTES = 32;
  private static final Pattern LOOKUP_PATTERN = Pattern.compile("[A-Za-z0-9_-]{16}");
  private static final Pattern SECRET_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");

  private ServerSdkKeyCredential() {}

  public static Generated generate(SecureRandom random, String pepperVersion, byte[] pepper) {
    Objects.requireNonNull(random, "random");
    requirePepperVersion(pepperVersion);
    byte[] lookupBytes = new byte[LOOKUP_BYTES];
    byte[] secretBytes = new byte[SECRET_BYTES];
    random.nextBytes(lookupBytes);
    random.nextBytes(secretBytes);
    String lookupId = Base64.getUrlEncoder().withoutPadding().encodeToString(lookupBytes);
    String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
    String credential = PREFIX + lookupId + '_' + secret;
    String fingerprint = PREFIX + lookupId.substring(0, 8) + "...";
    return new Generated(
        credential, lookupId, verifier(secret, pepper), fingerprint, pepperVersion);
  }

  public static Optional<String> lookupId(String credential) {
    Parsed parsed = parse(credential);
    return parsed == null ? Optional.empty() : Optional.of(parsed.lookupId());
  }

  public static boolean verify(String credential, byte[] expectedVerifier, byte[] pepper) {
    Objects.requireNonNull(expectedVerifier, "expectedVerifier");
    Parsed parsed = parse(credential);
    byte[] actual = verifier(parsed == null ? "" : parsed.secret(), pepper);
    boolean verifierMatches = MessageDigest.isEqual(expectedVerifier, actual);
    return (parsed != null) & verifierMatches;
  }

  private static Parsed parse(String credential) {
    if (credential == null || credential.length() != 67 || !credential.startsWith(PREFIX)) {
      return null;
    }
    int separator = PREFIX.length() + 16;
    if (credential.charAt(separator) != '_') {
      return null;
    }
    String lookupId = credential.substring(PREFIX.length(), separator);
    String secret = credential.substring(separator + 1);
    if (!LOOKUP_PATTERN.matcher(lookupId).matches() || !SECRET_PATTERN.matcher(secret).matches()) {
      return null;
    }
    return new Parsed(lookupId, secret);
  }

  private static byte[] verifier(String secret, byte[] pepper) {
    Objects.requireNonNull(pepper, "pepper");
    if (pepper.length < 32) {
      throw new IllegalArgumentException("SDK key pepper must contain at least 32 bytes");
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
      return mac.doFinal(secret.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
    }
  }

  private static void requirePepperVersion(String value) {
    if (value == null || !value.matches("[A-Za-z0-9._-]{1,32}")) {
      throw new IllegalArgumentException("Pepper version is invalid");
    }
  }

  private record Parsed(String lookupId, String secret) {}

  public static final class Generated {
    private final String credential;
    private final String lookupId;
    private final byte[] verifier;
    private final String fingerprint;
    private final String pepperVersion;

    private Generated(
        String credential,
        String lookupId,
        byte[] verifier,
        String fingerprint,
        String pepperVersion) {
      this.credential = credential;
      this.lookupId = lookupId;
      this.verifier = verifier.clone();
      this.fingerprint = fingerprint;
      this.pepperVersion = pepperVersion;
    }

    public String credential() {
      return credential;
    }

    public String lookupId() {
      return lookupId;
    }

    public byte[] verifier() {
      return verifier.clone();
    }

    public String fingerprint() {
      return fingerprint;
    }

    public String pepperVersion() {
      return pepperVersion;
    }

    @Override
    public String toString() {
      return "Generated{lookupId='" + lookupId + "', credential=<redacted>}";
    }
  }
}
