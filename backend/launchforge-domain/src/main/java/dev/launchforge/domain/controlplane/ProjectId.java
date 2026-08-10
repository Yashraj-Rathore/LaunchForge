package dev.launchforge.domain.controlplane;

import java.util.Objects;
import java.util.UUID;

public record ProjectId(UUID value) {
  public ProjectId {
    Objects.requireNonNull(value, "value");
  }

  public static ProjectId random() {
    return new ProjectId(UUID.randomUUID());
  }
}
