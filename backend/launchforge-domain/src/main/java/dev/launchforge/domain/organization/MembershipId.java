package dev.launchforge.domain.organization;

import java.util.Objects;
import java.util.UUID;

public record MembershipId(UUID value) {
  public MembershipId {
    Objects.requireNonNull(value, "value");
  }

  public static MembershipId random() {
    return new MembershipId(UUID.randomUUID());
  }
}
