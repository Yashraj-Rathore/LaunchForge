package dev.launchforge.controlapi.auth;

import dev.launchforge.application.organization.OrganizationAccess;
import dev.launchforge.application.organization.OrganizationQueryService;
import dev.launchforge.controlapi.security.OperatorIdentityResolver;
import dev.launchforge.domain.organization.OidcIdentity;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public final class AuthController {
  private final OperatorIdentityResolver identityResolver;
  private final OrganizationQueryService organizationQueryService;

  public AuthController(
      OperatorIdentityResolver identityResolver,
      OrganizationQueryService organizationQueryService) {
    this.identityResolver = identityResolver;
    this.organizationQueryService = organizationQueryService;
  }

  @GetMapping("/me")
  public SessionResponse currentSession(Authentication authentication) {
    OidcIdentity identity = identityResolver.requireIdentity(authentication);
    List<OrganizationResponse> organizations =
        organizationQueryService.listOrganizations(identity).stream()
            .map(OrganizationResponse::from)
            .toList();
    return new SessionResponse(
        identity.subject(), identityResolver.safeDisplayName(authentication), organizations);
  }

  @GetMapping("/csrf")
  public CsrfResponse csrf(CsrfToken token) {
    return new CsrfResponse(token.getHeaderName(), token.getToken());
  }

  public record SessionResponse(
      String subject, String displayName, List<OrganizationResponse> organizations) {}

  public record OrganizationResponse(String id, String slug, String name, String role) {
    private static OrganizationResponse from(OrganizationAccess access) {
      return new OrganizationResponse(
          access.organization().id().value().toString(),
          access.organization().slug().value(),
          access.organization().name(),
          access.actorRole().name());
    }
  }

  public record CsrfResponse(String headerName, String token) {}
}
