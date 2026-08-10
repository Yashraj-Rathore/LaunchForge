package dev.launchforge.domain.organization;

import java.util.EnumSet;
import java.util.Set;

public enum OrganizationRole {
  OWNER(EnumSet.allOf(OrganizationAbility.class)),
  ADMIN(
      EnumSet.of(
          OrganizationAbility.VIEW_CONFIGURATION,
          OrganizationAbility.EDIT_DRAFT,
          OrganizationAbility.PUBLISH_NON_PRODUCTION,
          OrganizationAbility.PUBLISH_PRODUCTION,
          OrganizationAbility.MANAGE_MEMBERS,
          OrganizationAbility.MANAGE_SDK_KEYS)),
  DEVELOPER(
      EnumSet.of(
          OrganizationAbility.VIEW_CONFIGURATION,
          OrganizationAbility.EDIT_DRAFT,
          OrganizationAbility.PUBLISH_NON_PRODUCTION,
          OrganizationAbility.MANAGE_SDK_KEYS)),
  VIEWER(EnumSet.of(OrganizationAbility.VIEW_CONFIGURATION));

  private final Set<OrganizationAbility> abilities;

  OrganizationRole(Set<OrganizationAbility> abilities) {
    this.abilities = Set.copyOf(abilities);
  }

  public boolean allows(OrganizationAbility ability) {
    return abilities.contains(ability);
  }
}
