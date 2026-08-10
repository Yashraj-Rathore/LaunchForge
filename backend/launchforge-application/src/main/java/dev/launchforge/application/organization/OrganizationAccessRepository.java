package dev.launchforge.application.organization;

import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.organization.OrganizationId;
import java.util.List;
import java.util.Optional;

public interface OrganizationAccessRepository {
  List<OrganizationAccess> findAllFor(OidcIdentity actor);

  Optional<OrganizationAccess> findFor(OidcIdentity actor, OrganizationId organizationId);
}
