package dev.launchforge.sdk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Language-neutral algorithm-version-1 rollout bucketing. */
public final class RolloutHasher {
  public static final int BUCKET_COUNT = 100_000;

  private RolloutHasher() {}

  public static int bucket(String flagKey, String salt, String subject) {
    requireMaterial(flagKey, "flagKey", true);
    requireMaterial(salt, "salt", true);
    requireSubject(subject);
    byte[] hash = sha256((flagKey + '\n' + salt + '\n' + subject).getBytes(StandardCharsets.UTF_8));
    long prefix = 0;
    for (int index = 0; index < Long.BYTES; index++) {
      prefix = (prefix << Byte.SIZE) | Byte.toUnsignedLong(hash[index]);
    }
    return (int) Long.remainderUnsigned(prefix, BUCKET_COUNT);
  }

  private static byte[] sha256(byte[] bytes) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
    }
  }

  private static void requireMaterial(String value, String label, boolean requireNonEmpty) {
    EvaluationContext.requireWellFormedUnicode(value, label);
    if ((requireNonEmpty && value.isEmpty())
        || value.indexOf('\n') >= 0
        || value.indexOf('\r') >= 0) {
      throw new IllegalArgumentException(label + " is invalid rollout material");
    }
  }

  private static void requireSubject(String subject) {
    EvaluationContext.requireWellFormedUnicode(subject, "subject");
    if (subject.isEmpty()) {
      throw new IllegalArgumentException("subject is invalid rollout material");
    }
  }
}
