package dev.launchforge.application.organization;

import dev.launchforge.domain.organization.MembershipId;
import dev.launchforge.domain.organization.OidcIdentity;

public interface AuditWriter {
  void appendMembershipEvent(
      OrganizationAccess access,
      OidcIdentity actor,
      MembershipAuditAction action,
      MembershipId targetMembershipId,
      String reasonCode);
}
