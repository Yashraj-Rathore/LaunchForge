package dev.launchforge.application.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.launchforge.domain.organization.MemberManagementPolicy;
import dev.launchforge.domain.organization.MembershipId;
import dev.launchforge.domain.organization.MembershipRoster;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.organization.Organization;
import dev.launchforge.domain.organization.OrganizationId;
import dev.launchforge.domain.organization.OrganizationMembership;
import dev.launchforge.domain.organization.OrganizationRole;
import dev.launchforge.domain.organization.OrganizationSlug;
import dev.launchforge.domain.organization.OrganizationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class MembershipAdministrationServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
  private static final String ISSUER = "https://identity.example/realms/launchforge";

  @Test
  void viewerCannotAddMemberAndDenialIsAuditedWithoutMutation() {
    Fixture fixture = new Fixture(OrganizationRole.VIEWER);

    assertThrows(
        OperationForbiddenException.class,
        () ->
            fixture.service.add(
                fixture.actor,
                fixture.organizationId,
                new OidcIdentity(ISSUER, "new-subject"),
                OrganizationRole.VIEWER));

    assertEquals(1, fixture.membershipRepository.members.size());
    assertEquals(List.of(MembershipAuditAction.MEMBERSHIP_ADD_DENIED), fixture.audit.actions);
  }

  @Test
  void clientOrganizationIdCannotGrantCrossTenantAuthority() {
    Fixture fixture = new Fixture(OrganizationRole.OWNER);

    assertThrows(
        OrganizationNotFoundException.class,
        () ->
            fixture.service.add(
                fixture.actor,
                OrganizationId.random(),
                new OidcIdentity(ISSUER, "new-subject"),
                OrganizationRole.VIEWER));
  }

  @Test
  void finalOwnerCannotBeRemovedAndDenialIsAudited() {
    Fixture fixture = new Fixture(OrganizationRole.OWNER);

    assertThrows(
        OrganizationConflictException.class,
        () ->
            fixture.service.remove(
                fixture.actor, fixture.organizationId, fixture.actorMembershipId));

    assertEquals(1, fixture.membershipRepository.members.size());
    assertEquals(List.of(MembershipAuditAction.MEMBERSHIP_REMOVE_DENIED), fixture.audit.actions);
  }

  private static final class Fixture {
    private final OrganizationId organizationId = OrganizationId.random();
    private final OidcIdentity actor = new OidcIdentity(ISSUER, "actor-subject");
    private final MembershipId actorMembershipId = MembershipId.random();
    private final FakeMembershipRepository membershipRepository;
    private final FakeAuditWriter audit = new FakeAuditWriter();
    private final MembershipAdministrationService service;

    private Fixture(OrganizationRole actorRole) {
      Organization organization =
          new Organization(
              organizationId,
              new OrganizationSlug("northstar"),
              "Northstar",
              OrganizationStatus.ACTIVE,
              0,
              NOW,
              NOW);
      OrganizationMembership actorMembership =
          new OrganizationMembership(actorMembershipId, organizationId, actor, actorRole, NOW, NOW);
      membershipRepository = new FakeMembershipRepository(actorMembership);
      OrganizationAccess access =
          new OrganizationAccess(organization, actorMembershipId, actorRole);
      OrganizationAccessRepository accessRepository =
          new OrganizationAccessRepository() {
            @Override
            public List<OrganizationAccess> findAllFor(OidcIdentity identity) {
              return identity.equals(actor) ? List.of(access) : List.of();
            }

            @Override
            public Optional<OrganizationAccess> findFor(
                OidcIdentity identity, OrganizationId requestedOrganizationId) {
              return identity.equals(actor) && organizationId.equals(requestedOrganizationId)
                  ? Optional.of(access)
                  : Optional.empty();
            }
          };
      service =
          new MembershipAdministrationService(
              accessRepository,
              membershipRepository,
              audit,
              new UnitOfWork() {
                @Override
                public <T> T required(Supplier<T> work) {
                  return work.get();
                }
              },
              new MemberManagementPolicy(),
              Clock.fixed(NOW, ZoneOffset.UTC));
    }
  }

  private static final class FakeMembershipRepository implements MembershipRepository {
    private final Map<MembershipId, OrganizationMembership> members = new LinkedHashMap<>();

    private FakeMembershipRepository(OrganizationMembership initialMember) {
      members.put(initialMember.id(), initialMember);
    }

    @Override
    public List<OrganizationMembership> findAll(OrganizationAccess access) {
      return List.copyOf(members.values());
    }

    @Override
    public MembershipRoster lockRoster(OrganizationAccess access) {
      return new MembershipRoster(access.organization().id(), members.values());
    }

    @Override
    public void insert(OrganizationAccess access, OrganizationMembership membership) {
      members.put(membership.id(), membership);
    }

    @Override
    public void updateRole(OrganizationAccess access, OrganizationMembership membership) {
      members.put(membership.id(), membership);
    }

    @Override
    public void delete(OrganizationAccess access, MembershipId membershipId) {
      members.remove(membershipId);
    }
  }

  private static final class FakeAuditWriter implements AuditWriter {
    private final List<MembershipAuditAction> actions = new ArrayList<>();

    @Override
    public void appendMembershipEvent(
        OrganizationAccess access,
        OidcIdentity actor,
        MembershipAuditAction action,
        MembershipId targetMembershipId,
        String reasonCode) {
      actions.add(action);
    }
  }
}
