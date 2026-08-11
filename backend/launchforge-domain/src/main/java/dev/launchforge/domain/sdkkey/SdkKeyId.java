package dev.launchforge.domain.sdkkey;

import java.util.Objects;
import java.util.UUID;

public record SdkKeyId(UUID value) {
  public SdkKeyId {
    Objects.requireNonNull(value, "value");
  }

  public static SdkKeyId random() {
    return new SdkKeyId(UUID.randomUUID());
  }
}
