package dev.launchforge.domain.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MembershipRosterTest {
  private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
  private static final OidcIdentity OWNER_IDENTITY =
      new OidcIdentity("https://identity.example/realms/launchforge", "owner-subject");

  @Test
  void rejectsRemovalOrDemotionOfFinalOwner() {
    OrganizationId organizationId = OrganizationId.random();
    OrganizationMembership owner = member(organizationId, OWNER_IDENTITY, OrganizationRole.OWNER);
    MembershipRoster roster = new MembershipRoster(organizationId, List.of(owner));

    assertThrows(DomainRuleViolationException.class, () -> roster.remove(owner.id()));
    assertThrows(
        DomainRuleViolationException.class,
        () -> roster.changeRole(owner.id(), OrganizationRole.ADMIN, NOW.plusSeconds(1)));
    assertEquals(OrganizationRole.OWNER, roster.requireMember(owner.id()).role());
  }

  @Test
  void permitsOwnerChangeWhenAnotherOwnerRemains() {
    OrganizationId organizationId = OrganizationId.random();
    OrganizationMembership first = member(organizationId, OWNER_IDENTITY, OrganizationRole.OWNER);
    OrganizationMembership second =
        member(
            organizationId,
            new OidcIdentity("https://identity.example/realms/launchforge", "second-owner"),
            OrganizationRole.OWNER);
    MembershipRoster roster = new MembershipRoster(organizationId, List.of(first, second));

    roster.changeRole(first.id(), OrganizationRole.ADMIN, NOW.plusSeconds(1));

    assertEquals(OrganizationRole.ADMIN, roster.requireMember(first.id()).role());
  }

  @Test
  void identityUniquenessUsesIssuerAndSubjectTogether() {
    OrganizationId organizationId = OrganizationId.random();
    OrganizationMembership owner = member(organizationId, OWNER_IDENTITY, OrganizationRole.OWNER);
    MembershipRoster roster = new MembershipRoster(organizationId, List.of(owner));

    assertThrows(
        DomainRuleViolationException.class,
        () -> roster.add(member(organizationId, OWNER_IDENTITY, OrganizationRole.VIEWER)));
    roster.add(
        member(
            organizationId,
            new OidcIdentity("https://second.example/realms/launchforge", "owner-subject"),
            OrganizationRole.VIEWER));

    assertEquals(2, roster.members().size());
  }

  private static OrganizationMembership member(
      OrganizationId organizationId, OidcIdentity identity, OrganizationRole role) {
    return new OrganizationMembership(
        MembershipId.random(), organizationId, identity, role, NOW, NOW);
  }
}
