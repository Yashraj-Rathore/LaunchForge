package dev.launchforge.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

class RolloutHasherTest {
  @Test
  void followsUnsignedBigEndianSha256ReferenceAlgorithm() throws Exception {
    String flag = "unicode-rollout";
    String salt = "stable_salt_1234";
    String subject = "Zoë-東京";
    int expected = referenceBucket(flag, salt, subject);

    int actual = RolloutHasher.bucket(flag, salt, subject);

    assertEquals(expected, actual);
    assertTrue(actual >= 0 && actual < 100_000);
    assertNotEquals(actual, RolloutHasher.bucket(flag, "changed_salt_1234", subject));
    assertEquals(
        referenceBucket(flag, salt, "line-one\nline-two"),
        RolloutHasher.bucket(flag, salt, "line-one\nline-two"));
  }

  @Test
  void rejectsAmbiguousMaterial() {
    assertThrows(
        IllegalArgumentException.class,
        () -> RolloutHasher.bucket("flag\nother", "stable_salt_1234", "subject"));
    assertThrows(
        IllegalArgumentException.class, () -> RolloutHasher.bucket("flag", "stable_salt_1234", ""));
  }

  static int referenceBucket(String flagKey, String salt, String subject) throws Exception {
    byte[] digest =
        MessageDigest.getInstance("SHA-256")
            .digest((flagKey + '\n' + salt + '\n' + subject).getBytes(StandardCharsets.UTF_8));
    long prefix = 0;
    for (int index = 0; index < 8; index++) {
      prefix = (prefix << 8) | Byte.toUnsignedLong(digest[index]);
    }
    return (int) Long.remainderUnsigned(prefix, 100_000);
  }
}
