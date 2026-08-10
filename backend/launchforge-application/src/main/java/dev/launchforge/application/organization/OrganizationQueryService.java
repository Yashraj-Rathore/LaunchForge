package dev.launchforge.application.organization;

import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.organization.OrganizationAbility;
import dev.launchforge.domain.organization.OrganizationId;
import dev.launchforge.domain.organization.OrganizationMembership;
import java.util.List;
import java.util.Objects;

public final class OrganizationQueryService {
  private final OrganizationAccessRepository accessRepository;
  private final MembershipRepository membershipRepository;

  public OrganizationQueryService(
      OrganizationAccessRepository accessRepository, MembershipRepository membershipRepository) {
    this.accessRepository = Objects.requireNonNull(accessRepository, "accessRepository");
    this.membershipRepository =
        Objects.requireNonNull(membershipRepository, "membershipRepository");
  }

  public List<OrganizationAccess> listOrganizations(OidcIdentity actor) {
    return List.copyOf(accessRepository.findAllFor(actor));
  }

  public OrganizationAccess requireOrganization(OidcIdentity actor, OrganizationId organizationId) {
    return accessRepository
        .findFor(actor, organizationId)
        .orElseThrow(OrganizationNotFoundException::new);
  }

  public List<OrganizationMembership> listMemberships(
      OidcIdentity actor, OrganizationId organizationId) {
    OrganizationAccess access = requireOrganization(actor, organizationId);
    if (!access.actorRole().allows(OrganizationAbility.MANAGE_MEMBERS)) {
      throw new OperationForbiddenException();
    }
    return List.copyOf(membershipRepository.findAll(access));
  }
}
