package dev.launchforge.infrastructure.controlplane;

import dev.launchforge.application.controlplane.RolloutSaltGenerator;
import java.security.SecureRandom;
import java.util.Base64;

public final class SecureRolloutSaltGenerator implements RolloutSaltGenerator {
  private final SecureRandom secureRandom;

  public SecureRolloutSaltGenerator() {
    this(new SecureRandom());
  }

  SecureRolloutSaltGenerator(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  @Override
  public String generate() {
    byte[] entropy = new byte[24];
    secureRandom.nextBytes(entropy);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
  }
}
