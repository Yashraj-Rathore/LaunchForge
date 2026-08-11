package dev.launchforge.application.sdkkey;

import dev.launchforge.domain.sdkkey.ServerSdkKey;
import java.util.Objects;

public final class IssuedServerSdkKey {
  private final ServerSdkKey metadata;
  private final String credential;

  public IssuedServerSdkKey(ServerSdkKey metadata, String credential) {
    this.metadata = Objects.requireNonNull(metadata, "metadata");
    this.credential = Objects.requireNonNull(credential, "credential");
  }

  public ServerSdkKey metadata() {
    return metadata;
  }

  public String credential() {
    return credential;
  }

  @Override
  public String toString() {
    return "IssuedServerSdkKey{keyId=" + metadata.id().value() + ", credential=<redacted>}";
  }
}
