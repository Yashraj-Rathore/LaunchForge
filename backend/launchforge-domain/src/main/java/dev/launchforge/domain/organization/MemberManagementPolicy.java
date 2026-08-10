package dev.launchforge.domain.organization;

import java.util.Objects;

public final class MemberManagementPolicy {
  public boolean mayAdd(OrganizationRole actorRole, OrganizationRole requestedRole) {
    Objects.requireNonNull(actorRole, "actorRole");
    Objects.requireNonNull(requestedRole, "requestedRole");
    return actorRole.allows(OrganizationAbility.MANAGE_MEMBERS)
        && (requestedRole != OrganizationRole.OWNER || actorRole == OrganizationRole.OWNER);
  }

  public boolean mayChange(
      OrganizationRole actorRole,
      OrganizationRole currentTargetRole,
      OrganizationRole requestedRole) {
    Objects.requireNonNull(actorRole, "actorRole");
    Objects.requireNonNull(currentTargetRole, "currentTargetRole");
    Objects.requireNonNull(requestedRole, "requestedRole");
    if (!actorRole.allows(OrganizationAbility.MANAGE_MEMBERS)) {
      return false;
    }
    if (currentTargetRole == OrganizationRole.OWNER || requestedRole == OrganizationRole.OWNER) {
      return actorRole == OrganizationRole.OWNER;
    }
    return actorRole == OrganizationRole.OWNER || actorRole == OrganizationRole.ADMIN;
  }

  public boolean mayRemove(OrganizationRole actorRole, OrganizationRole currentTargetRole) {
    Objects.requireNonNull(actorRole, "actorRole");
    Objects.requireNonNull(currentTargetRole, "currentTargetRole");
    if (!actorRole.allows(OrganizationAbility.MANAGE_MEMBERS)) {
      return false;
    }
    return currentTargetRole != OrganizationRole.OWNER || actorRole == OrganizationRole.OWNER;
  }
}
