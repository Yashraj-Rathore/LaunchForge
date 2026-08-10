package dev.launchforge.application.organization;

import dev.launchforge.domain.organization.MembershipId;
import dev.launchforge.domain.organization.Organization;
import dev.launchforge.domain.organization.OrganizationRole;
import java.util.Objects;

public record OrganizationAccess(
    Organization organization, MembershipId actorMembershipId, OrganizationRole actorRole) {
  public OrganizationAccess {
    Objects.requireNonNull(organization, "organization");
    Objects.requireNonNull(actorMembershipId, "actorMembershipId");
    Objects.requireNonNull(actorRole, "actorRole");
  }
}
