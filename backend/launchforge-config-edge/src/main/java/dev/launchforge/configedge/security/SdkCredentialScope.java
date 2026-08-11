package dev.launchforge.configedge.security;

import java.util.Objects;
import java.util.UUID;

public record SdkCredentialScope(UUID keyId, UUID environmentId) {
  public SdkCredentialScope {
    Objects.requireNonNull(keyId, "keyId");
    Objects.requireNonNull(environmentId, "environmentId");
  }
}
