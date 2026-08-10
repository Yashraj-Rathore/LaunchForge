package dev.launchforge.domain.organization;

import java.time.Instant;
import java.util.Objects;

public record OrganizationMembership(
    MembershipId id,
    OrganizationId organizationId,
    OidcIdentity identity,
    OrganizationRole role,
    Instant createdAt,
    Instant updatedAt) {
  public OrganizationMembership {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(organizationId, "organizationId");
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    if (updatedAt.isBefore(createdAt)) {
      throw new DomainRuleViolationException("Membership update time cannot precede creation");
    }
  }

  public OrganizationMembership withRole(OrganizationRole newRole, Instant now) {
    return new OrganizationMembership(id, organizationId, identity, newRole, createdAt, now);
  }
}
