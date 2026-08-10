package dev.launchforge.domain.organization;

import java.util.Objects;
import java.util.UUID;

public record OrganizationId(UUID value) {
  public OrganizationId {
    Objects.requireNonNull(value, "value");
  }

  public static OrganizationId random() {
    return new OrganizationId(UUID.randomUUID());
  }
}
