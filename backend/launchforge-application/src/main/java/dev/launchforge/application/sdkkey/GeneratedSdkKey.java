package dev.launchforge.application.sdkkey;

import java.util.Objects;

public final class GeneratedSdkKey {
  private final String credential;
  private final String lookupId;
  private final byte[] verifier;
  private final String fingerprint;
  private final String pepperVersion;

  public GeneratedSdkKey(
      String credential,
      String lookupId,
      byte[] verifier,
      String fingerprint,
      String pepperVersion) {
    this.credential = Objects.requireNonNull(credential, "credential");
    this.lookupId = Objects.requireNonNull(lookupId, "lookupId");
    this.verifier = Objects.requireNonNull(verifier, "verifier").clone();
    this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
    this.pepperVersion = Objects.requireNonNull(pepperVersion, "pepperVersion");
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
    return "GeneratedSdkKey{lookupId='" + lookupId + "', credential=<redacted>}";
  }
}
