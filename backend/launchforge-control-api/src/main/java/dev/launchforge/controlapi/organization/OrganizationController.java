package dev.launchforge.controlapi.organization;

import dev.launchforge.application.organization.MembershipAdministrationService;
import dev.launchforge.application.organization.OrganizationAccess;
import dev.launchforge.application.organization.OrganizationQueryService;
import dev.launchforge.controlapi.security.OperatorIdentityResolver;
import dev.launchforge.domain.organization.MembershipId;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.organization.OrganizationId;
import dev.launchforge.domain.organization.OrganizationMembership;
import dev.launchforge.domain.organization.OrganizationRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public final class OrganizationController {
  private final OperatorIdentityResolver identityResolver;
  private final OrganizationQueryService queryService;
  private final MembershipAdministrationService membershipService;

  public OrganizationController(
      OperatorIdentityResolver identityResolver,
      OrganizationQueryService queryService,
      MembershipAdministrationService membershipService) {
    this.identityResolver = identityResolver;
    this.queryService = queryService;
    this.membershipService = membershipService;
  }

  @GetMapping
  public List<OrganizationResponse> organizations(Authentication authentication) {
    OidcIdentity actor = identityResolver.requireIdentity(authentication);
    return queryService.listOrganizations(actor).stream().map(OrganizationResponse::from).toList();
  }

  @GetMapping("/{organizationId}")
  public OrganizationResponse organization(
      Authentication authentication, @PathVariable UUID organizationId) {
    OidcIdentity actor = identityResolver.requireIdentity(authentication);
    return OrganizationResponse.from(
        queryService.requireOrganization(actor, new OrganizationId(organizationId)));
  }

  @GetMapping("/{organizationId}/members")
  public List<MembershipResponse> memberships(
      Authentication authentication, @PathVariable UUID organizationId) {
    OidcIdentity actor = identityResolver.requireIdentity(authentication);
    return queryService.listMemberships(actor, new OrganizationId(organizationId)).stream()
        .map(MembershipResponse::from)
        .toList();
  }

  @PostMapping("/{organizationId}/members")
  @ResponseStatus(HttpStatus.CREATED)
  public MembershipResponse addMembership(
      Authentication authentication,
      @PathVariable UUID organizationId,
      @RequestBody AddMembershipRequest request) {
    OidcIdentity actor = identityResolver.requireIdentity(authentication);
    OrganizationMembership membership =
        membershipService.add(
            actor,
            new OrganizationId(organizationId),
            new OidcIdentity(request.issuer(), request.subject()),
            request.role());
    return MembershipResponse.from(membership);
  }

  @PatchMapping("/{organizationId}/members/{membershipId}")
  public MembershipResponse changeRole(
      Authentication authentication,
      HttpServletRequest servletRequest,
      @PathVariable UUID organizationId,
      @PathVariable UUID membershipId,
      @RequestBody ChangeRoleRequest request) {
    OidcIdentity actor = identityResolver.requireIdentity(authentication);
    OrganizationId scopedOrganizationId = new OrganizationId(organizationId);
    OrganizationAccess access = queryService.requireOrganization(actor, scopedOrganizationId);
    OrganizationMembership membership =
        membershipService.changeRole(
            actor, scopedOrganizationId, new MembershipId(membershipId), request.role());
    if (access.actorMembershipId().equals(membership.id())) {
      servletRequest.changeSessionId();
    }
    return MembershipResponse.from(membership);
  }

  @DeleteMapping("/{organizationId}/members/{membershipId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeMembership(
      Authentication authentication,
      @PathVariable UUID organizationId,
      @PathVariable UUID membershipId) {
    membershipService.remove(
        identityResolver.requireIdentity(authentication),
        new OrganizationId(organizationId),
        new MembershipId(membershipId));
  }

  public record AddMembershipRequest(String issuer, String subject, OrganizationRole role) {}

  public record ChangeRoleRequest(OrganizationRole role) {}

  public record OrganizationResponse(
      String id, String slug, String name, String status, long version, String role) {
    private static OrganizationResponse from(OrganizationAccess access) {
      return new OrganizationResponse(
          access.organization().id().value().toString(),
          access.organization().slug().value(),
          access.organization().name(),
          access.organization().status().name(),
          access.organization().version(),
          access.actorRole().name());
    }
  }

  public record MembershipResponse(
      String id, String issuer, String subject, String role, String createdAt) {
    private static MembershipResponse from(OrganizationMembership membership) {
      return new MembershipResponse(
          membership.id().value().toString(),
          membership.identity().issuer(),
          membership.identity().subject(),
          membership.role().name(),
          membership.createdAt().toString());
    }
  }
}
