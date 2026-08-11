package dev.launchforge.infrastructure.sdkkey;

import dev.launchforge.application.sdkkey.GeneratedSdkKey;
import dev.launchforge.application.sdkkey.ServerSdkKeyGenerator;
import dev.launchforge.contracts.sdk.ServerSdkKeyCredential;
import java.security.SecureRandom;
import java.util.Objects;

public final class SecureServerSdkKeyGenerator implements ServerSdkKeyGenerator {
  private final SecureRandom random;
  private final String pepperVersion;
  private final byte[] pepper;

  public SecureServerSdkKeyGenerator(String pepperVersion, byte[] pepper) {
    this(new SecureRandom(), pepperVersion, pepper);
  }

  SecureServerSdkKeyGenerator(SecureRandom random, String pepperVersion, byte[] pepper) {
    this.random = Objects.requireNonNull(random, "random");
    this.pepperVersion = Objects.requireNonNull(pepperVersion, "pepperVersion");
    this.pepper = Objects.requireNonNull(pepper, "pepper").clone();
    if (this.pepper.length < 32) {
      throw new IllegalArgumentException("SDK key pepper must contain at least 32 bytes");
    }
  }

  @Override
  public GeneratedSdkKey generate() {
    ServerSdkKeyCredential.Generated generated =
        ServerSdkKeyCredential.generate(random, pepperVersion, pepper);
    return new GeneratedSdkKey(
        generated.credential(),
        generated.lookupId(),
        generated.verifier(),
        generated.fingerprint(),
        generated.pepperVersion());
  }
}
