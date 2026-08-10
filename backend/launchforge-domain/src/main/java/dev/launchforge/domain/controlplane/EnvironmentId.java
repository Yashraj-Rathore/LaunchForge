package dev.launchforge.domain.controlplane;

import java.util.Objects;
import java.util.UUID;

public record EnvironmentId(UUID value) {
  public EnvironmentId {
    Objects.requireNonNull(value, "value");
  }

  public static EnvironmentId random() {
    return new EnvironmentId(UUID.randomUUID());
  }
}
