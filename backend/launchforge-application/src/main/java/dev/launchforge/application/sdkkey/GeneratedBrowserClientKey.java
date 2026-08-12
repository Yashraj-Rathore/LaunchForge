package dev.launchforge.application.sdkkey;

import java.util.Objects;

public record GeneratedBrowserClientKey(String clientKey, String fingerprint) {
  public GeneratedBrowserClientKey {
    Objects.requireNonNull(clientKey, "clientKey");
    Objects.requireNonNull(fingerprint, "fingerprint");
  }
}
