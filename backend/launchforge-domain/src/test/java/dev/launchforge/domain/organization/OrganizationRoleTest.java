package dev.launchforge.domain.organization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrganizationRoleTest {
  @Test
  void roleAbilitiesMatchTheSecurityMatrix() {
    assertTrue(OrganizationRole.OWNER.allows(OrganizationAbility.DELETE_ORGANIZATION));
    assertTrue(OrganizationRole.ADMIN.allows(OrganizationAbility.PUBLISH_PRODUCTION));
    assertTrue(OrganizationRole.DEVELOPER.allows(OrganizationAbility.PUBLISH_NON_PRODUCTION));
    assertFalse(OrganizationRole.DEVELOPER.allows(OrganizationAbility.PUBLISH_PRODUCTION));
    assertFalse(OrganizationRole.VIEWER.allows(OrganizationAbility.EDIT_DRAFT));
  }

  @Test
  void onlyOwnersCanGrantOrModifyOwnerMemberships() {
    MemberManagementPolicy policy = new MemberManagementPolicy();

    assertTrue(policy.mayAdd(OrganizationRole.OWNER, OrganizationRole.OWNER));
    assertFalse(policy.mayAdd(OrganizationRole.ADMIN, OrganizationRole.OWNER));
    assertFalse(
        policy.mayChange(OrganizationRole.ADMIN, OrganizationRole.OWNER, OrganizationRole.ADMIN));
    assertTrue(
        policy.mayChange(OrganizationRole.OWNER, OrganizationRole.OWNER, OrganizationRole.ADMIN));
  }
}
