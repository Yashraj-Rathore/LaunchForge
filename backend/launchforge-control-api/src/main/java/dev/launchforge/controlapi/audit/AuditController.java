package dev.launchforge.controlapi.audit;

import dev.launchforge.application.controlplane.AuditEvent;
import dev.launchforge.application.controlplane.AuditQuery;
import dev.launchforge.application.controlplane.ControlPlaneService;
import dev.launchforge.controlapi.security.OperatorIdentityResolver;
import dev.launchforge.domain.organization.OrganizationId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/audit")
public final class AuditController {
  private final OperatorIdentityResolver identityResolver;
  private final ControlPlaneService service;

  public AuditController(OperatorIdentityResolver identityResolver, ControlPlaneService service) {
    this.identityResolver = identityResolver;
    this.service = service;
  }

  @GetMapping
  public List<AuditEventResponse> audit(
      Authentication authentication,
      @PathVariable UUID organizationId,
      @RequestParam(required = false) UUID projectId,
      @RequestParam(required = false) UUID environmentId,
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "100") int limit) {
    AuditQuery query = new AuditQuery(projectId, environmentId, actor, action, from, to, limit);
    return service
        .auditHistory(
            identityResolver.requireIdentity(authentication),
            new OrganizationId(organizationId),
            query)
        .stream()
        .map(AuditEventResponse::from)
        .toList();
  }

  public record AuditEventResponse(
      UUID id,
      UUID projectId,
      UUID environmentId,
      String actor,
      String action,
      String targetType,
      UUID targetId,
      String summary,
      String reason,
      Long fromRevision,
      Long toRevision,
      UUID correlationId,
      Instant createdAt) {
    private static AuditEventResponse from(AuditEvent event) {
      return new AuditEventResponse(
          event.id(),
          event.projectId(),
          event.environmentId(),
          event.actorSubject(),
          event.action(),
          event.targetType(),
          event.targetId(),
          event.safeSummary(),
          event.humanReason(),
          event.fromRevision(),
          event.toRevision(),
          event.correlationId(),
          event.createdAt());
    }
  }
}
