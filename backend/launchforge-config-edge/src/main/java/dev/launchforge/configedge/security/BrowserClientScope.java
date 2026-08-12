package dev.launchforge.configedge.security;

import java.util.Objects;
import java.util.UUID;

public record BrowserClientScope(UUID keyId, UUID environmentId) {
  public BrowserClientScope {
    Objects.requireNonNull(keyId, "keyId");
    Objects.requireNonNull(environmentId, "environmentId");
  }
}
