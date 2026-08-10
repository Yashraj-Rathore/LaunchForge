package dev.launchforge.controlapi.security;

import dev.launchforge.domain.organization.OidcIdentity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public final class OperatorIdentityResolver {
  public OidcIdentity requireIdentity(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
      throw new IllegalStateException("Authenticated principal is not an OIDC user");
    }
    return new OidcIdentity(
        oidcUser.getIdToken().getIssuer().toString(), oidcUser.getIdToken().getSubject());
  }

  public String safeDisplayName(Authentication authentication) {
    OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
    String preferredUsername = oidcUser.getClaimAsString("preferred_username");
    if (preferredUsername == null
        || preferredUsername.isBlank()
        || preferredUsername.length() > 120) {
      return "Operator";
    }
    return preferredUsername;
  }
}
