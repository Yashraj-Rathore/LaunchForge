package dev.launchforge.infrastructure.sdkkey;

import dev.launchforge.application.sdkkey.BrowserClientKeyGenerator;
import dev.launchforge.application.sdkkey.GeneratedBrowserClientKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class SecureBrowserClientKeyGenerator implements BrowserClientKeyGenerator {
  private final SecureRandom secureRandom;

  public SecureBrowserClientKeyGenerator() {
    this(new SecureRandom());
  }

  SecureBrowserClientKeyGenerator(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  @Override
  public GeneratedBrowserClientKey generate() {
    byte[] entropy = new byte[24];
    secureRandom.nextBytes(entropy);
    String clientKey =
        "lf_client_" + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    return new GeneratedBrowserClientKey(clientKey, fingerprint(clientKey));
  }

  private static String fingerprint(String clientKey) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(clientKey.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 12);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
    }
  }
}
