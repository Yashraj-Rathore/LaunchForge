package dev.launchforge.domain.organization;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MembershipRoster {
  private final OrganizationId organizationId;
  private final Map<MembershipId, OrganizationMembership> members;

  public MembershipRoster(
      OrganizationId organizationId, Collection<OrganizationMembership> initialMembers) {
    this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
    Objects.requireNonNull(initialMembers, "initialMembers");
    this.members = new LinkedHashMap<>();
    for (OrganizationMembership member : initialMembers) {
      requireSameOrganization(member);
      if (members.putIfAbsent(member.id(), member) != null) {
        throw new DomainRuleViolationException("Membership IDs must be unique");
      }
      requireIdentityUnique(member.identity(), member.id());
    }
    requireOwnerExists();
  }

  public void add(OrganizationMembership membership) {
    requireSameOrganization(membership);
    if (members.containsKey(membership.id())) {
      throw new DomainRuleViolationException("Membership ID is already present");
    }
    requireIdentityUnique(membership.identity(), membership.id());
    members.put(membership.id(), membership);
  }

  public OrganizationMembership changeRole(
      MembershipId membershipId, OrganizationRole newRole, Instant now) {
    OrganizationMembership current = requireMember(membershipId);
    OrganizationMembership changed = current.withRole(newRole, now);
    members.put(membershipId, changed);
    try {
      requireOwnerExists();
    } catch (DomainRuleViolationException exception) {
      members.put(membershipId, current);
      throw exception;
    }
    return changed;
  }

  public OrganizationMembership remove(MembershipId membershipId) {
    OrganizationMembership removed = requireMember(membershipId);
    members.remove(membershipId);
    try {
      requireOwnerExists();
    } catch (DomainRuleViolationException exception) {
      members.put(membershipId, removed);
      throw exception;
    }
    return removed;
  }

  public OrganizationMembership requireMember(MembershipId membershipId) {
    OrganizationMembership member = members.get(membershipId);
    if (member == null) {
      throw new DomainRuleViolationException("Membership does not exist in this organization");
    }
    return member;
  }

  public List<OrganizationMembership> members() {
    return List.copyOf(members.values());
  }

  private void requireOwnerExists() {
    if (members.values().stream().noneMatch(member -> member.role() == OrganizationRole.OWNER)) {
      throw new DomainRuleViolationException("An organization must retain at least one Owner");
    }
  }

  private void requireIdentityUnique(OidcIdentity identity, MembershipId ignoredMembershipId) {
    boolean duplicate =
        members.values().stream()
            .anyMatch(
                member ->
                    !member.id().equals(ignoredMembershipId) && member.identity().equals(identity));
    if (duplicate) {
      throw new DomainRuleViolationException(
          "OIDC identity already has a membership in this organization");
    }
  }

  private void requireSameOrganization(OrganizationMembership membership) {
    Objects.requireNonNull(membership, "membership");
    if (!organizationId.equals(membership.organizationId())) {
      throw new DomainRuleViolationException("Membership belongs to a different organization");
    }
  }
}
