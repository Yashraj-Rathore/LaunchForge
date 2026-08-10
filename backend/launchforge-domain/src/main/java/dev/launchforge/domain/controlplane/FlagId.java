package dev.launchforge.domain.controlplane;

import java.util.Objects;
import java.util.UUID;

public record FlagId(UUID value) {
  public FlagId {
    Objects.requireNonNull(value, "value");
  }

  public static FlagId random() {
    return new FlagId(UUID.randomUUID());
  }
}
