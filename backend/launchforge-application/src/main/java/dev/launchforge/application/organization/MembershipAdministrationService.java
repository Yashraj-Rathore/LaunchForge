package dev.launchforge.application.organization;

import dev.launchforge.domain.organization.DomainRuleViolationException;
import dev.launchforge.domain.organization.MemberManagementPolicy;
import dev.launchforge.domain.organization.MembershipId;
import dev.launchforge.domain.organization.MembershipRoster;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.organization.OrganizationId;
import dev.launchforge.domain.organization.OrganizationMembership;
import dev.launchforge.domain.organization.OrganizationRole;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class MembershipAdministrationService {
  private static final String ROLE_POLICY_DENIED = "ROLE_POLICY_DENIED";
  private static final String DOMAIN_INVARIANT_DENIED = "DOMAIN_INVARIANT_DENIED";

  private final OrganizationAccessRepository accessRepository;
  private final MembershipRepository membershipRepository;
  private final AuditWriter auditWriter;
  private final UnitOfWork unitOfWork;
  private final MemberManagementPolicy policy;
  private final Clock clock;

  public MembershipAdministrationService(
      OrganizationAccessRepository accessRepository,
      MembershipRepository membershipRepository,
      AuditWriter auditWriter,
      UnitOfWork unitOfWork,
      MemberManagementPolicy policy,
      Clock clock) {
    this.accessRepository = Objects.requireNonNull(accessRepository, "accessRepository");
    this.membershipRepository =
        Objects.requireNonNull(membershipRepository, "membershipRepository");
    this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
    this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    this.policy = Objects.requireNonNull(policy, "policy");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public OrganizationMembership add(
      OidcIdentity actor,
      OrganizationId organizationId,
      OidcIdentity newIdentity,
      OrganizationRole requestedRole) {
    OperationResult<OrganizationMembership> result =
        unitOfWork.required(
            () -> {
              OrganizationAccess access = requireAccess(actor, organizationId);
              MembershipId membershipId = MembershipId.random();
              if (!policy.mayAdd(access.actorRole(), requestedRole)) {
                auditWriter.appendMembershipEvent(
                    access,
                    actor,
                    MembershipAuditAction.MEMBERSHIP_ADD_DENIED,
                    membershipId,
                    ROLE_POLICY_DENIED);
                return OperationResult.forbidden();
              }
              Instant now = clock.instant();
              OrganizationMembership membership =
                  new OrganizationMembership(
                      membershipId, organizationId, newIdentity, requestedRole, now, now);
              MembershipRoster roster = membershipRepository.lockRoster(access);
              try {
                roster.add(membership);
              } catch (DomainRuleViolationException exception) {
                return deniedConflict(
                    access,
                    actor,
                    MembershipAuditAction.MEMBERSHIP_ADD_DENIED,
                    membershipId,
                    exception);
              }
              membershipRepository.insert(access, membership);
              auditWriter.appendMembershipEvent(
                  access, actor, MembershipAuditAction.MEMBERSHIP_ADDED, membershipId, "SUCCESS");
              return OperationResult.success(membership);
            });
    return result.complete();
  }

  public OrganizationMembership changeRole(
      OidcIdentity actor,
      OrganizationId organizationId,
      MembershipId membershipId,
      OrganizationRole requestedRole) {
    OperationResult<OrganizationMembership> result =
        unitOfWork.required(
            () -> {
              OrganizationAccess access = requireAccess(actor, organizationId);
              MembershipRoster roster = membershipRepository.lockRoster(access);
              OrganizationMembership target = requireTarget(roster, membershipId);
              if (!policy.mayChange(access.actorRole(), target.role(), requestedRole)) {
                auditWriter.appendMembershipEvent(
                    access,
                    actor,
                    MembershipAuditAction.MEMBERSHIP_ROLE_CHANGE_DENIED,
                    membershipId,
                    ROLE_POLICY_DENIED);
                return OperationResult.forbidden();
              }
              OrganizationMembership changed;
              try {
                changed = roster.changeRole(membershipId, requestedRole, clock.instant());
              } catch (DomainRuleViolationException exception) {
                return deniedConflict(
                    access,
                    actor,
                    MembershipAuditAction.MEMBERSHIP_ROLE_CHANGE_DENIED,
                    membershipId,
                    exception);
              }
              membershipRepository.updateRole(access, changed);
              auditWriter.appendMembershipEvent(
                  access,
                  actor,
                  MembershipAuditAction.MEMBERSHIP_ROLE_CHANGED,
                  membershipId,
                  "SUCCESS");
              return OperationResult.success(changed);
            });
    return result.complete();
  }

  public void remove(OidcIdentity actor, OrganizationId organizationId, MembershipId membershipId) {
    OperationResult<Void> result =
        unitOfWork.required(
            () -> {
              OrganizationAccess access = requireAccess(actor, organizationId);
              MembershipRoster roster = membershipRepository.lockRoster(access);
              OrganizationMembership target = requireTarget(roster, membershipId);
              if (!policy.mayRemove(access.actorRole(), target.role())) {
                auditWriter.appendMembershipEvent(
                    access,
                    actor,
                    MembershipAuditAction.MEMBERSHIP_REMOVE_DENIED,
                    membershipId,
                    ROLE_POLICY_DENIED);
                return OperationResult.forbidden();
              }
              try {
                roster.remove(membershipId);
              } catch (DomainRuleViolationException exception) {
                return deniedConflict(
                    access,
                    actor,
                    MembershipAuditAction.MEMBERSHIP_REMOVE_DENIED,
                    membershipId,
                    exception);
              }
              membershipRepository.delete(access, membershipId);
              auditWriter.appendMembershipEvent(
                  access, actor, MembershipAuditAction.MEMBERSHIP_REMOVED, membershipId, "SUCCESS");
              return OperationResult.success(null);
            });
    result.complete();
  }

  private OrganizationAccess requireAccess(OidcIdentity actor, OrganizationId organizationId) {
    return accessRepository
        .findFor(actor, organizationId)
        .orElseThrow(OrganizationNotFoundException::new);
  }

  private static OrganizationMembership requireTarget(
      MembershipRoster roster, MembershipId membershipId) {
    try {
      return roster.requireMember(membershipId);
    } catch (DomainRuleViolationException exception) {
      throw new OrganizationConflictException(exception.getMessage());
    }
  }

  private <T> OperationResult<T> deniedConflict(
      OrganizationAccess access,
      OidcIdentity actor,
      MembershipAuditAction action,
      MembershipId membershipId,
      DomainRuleViolationException exception) {
    auditWriter.appendMembershipEvent(access, actor, action, membershipId, DOMAIN_INVARIANT_DENIED);
    return OperationResult.conflict(exception.getMessage());
  }

  private record OperationResult<T>(T value, RuntimeException failure) {
    private static <T> OperationResult<T> success(T value) {
      return new OperationResult<>(value, null);
    }

    private static <T> OperationResult<T> forbidden() {
      return new OperationResult<>(null, new OperationForbiddenException());
    }

    private static <T> OperationResult<T> conflict(String message) {
      return new OperationResult<>(null, new OrganizationConflictException(message));
    }

    private T complete() {
      if (failure != null) {
        throw failure;
      }
      return value;
    }
  }
}
