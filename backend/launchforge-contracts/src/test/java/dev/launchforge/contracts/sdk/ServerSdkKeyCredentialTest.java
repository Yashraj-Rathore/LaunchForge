package dev.launchforge.contracts.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class ServerSdkKeyCredentialTest {
  private static final byte[] PEPPER =
      "test-pepper-that-is-at-least-thirty-two-bytes".getBytes(StandardCharsets.UTF_8);

  @Test
  void generatedCredentialUsesVersionOneShapeAndVerifiesWithoutStoredSecret() {
    ServerSdkKeyCredential.Generated generated =
        ServerSdkKeyCredential.generate(new SecureRandom(), "v1", PEPPER);

    assertTrue(generated.credential().matches("lf_srv_[A-Za-z0-9_-]{16}_[A-Za-z0-9_-]{43}"));
    assertTrue(ServerSdkKeyCredential.verify(generated.credential(), generated.verifier(), PEPPER));
    assertTrue(
        ServerSdkKeyCredential.lookupId(generated.credential())
            .filter(generated.lookupId()::equals)
            .isPresent());
    assertFalse(generated.toString().contains(generated.credential()));
  }

  @Test
  void wrongSecretAndPepperAreRejectedAndVerifierIsDefensivelyCopied() {
    ServerSdkKeyCredential.Generated generated =
        ServerSdkKeyCredential.generate(new SecureRandom(), "v1", PEPPER);
    byte[] original = generated.verifier();
    byte[] modified = generated.verifier();
    modified[0] ^= 1;

    assertArrayEquals(original, generated.verifier());
    assertFalse(ServerSdkKeyCredential.verify(generated.credential() + "x", original, PEPPER));
    assertFalse(
        ServerSdkKeyCredential.verify(
            generated.credential(),
            original,
            "different-pepper-that-is-also-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)));
    assertNotEquals(java.util.HexFormat.of().formatHex(original), generated.credential());
  }
}
