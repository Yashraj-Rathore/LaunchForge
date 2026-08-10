package dev.launchforge.application.organization;

import dev.launchforge.domain.organization.MembershipId;
import dev.launchforge.domain.organization.MembershipRoster;
import dev.launchforge.domain.organization.OrganizationMembership;
import java.util.List;

public interface MembershipRepository {
  List<OrganizationMembership> findAll(OrganizationAccess access);

  MembershipRoster lockRoster(OrganizationAccess access);

  void insert(OrganizationAccess access, OrganizationMembership membership);

  void updateRole(OrganizationAccess access, OrganizationMembership membership);

  void delete(OrganizationAccess access, MembershipId membershipId);
}
