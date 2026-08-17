package dev.launchforge.controlapi.audit;

import dev.launchforge.application.controlplane.AuditEvent;
import dev.launchforge.application.controlplane.AuditQuery;
import dev.launchforge.application.controlplane.AuditRetentionPreview;
import dev.launchforge.application.controlplane.AuditRetentionResult;
import dev.launchforge.application.controlplane.AuditRetentionService;
import dev.launchforge.application.controlplane.ControlPlaneService;
import dev.launchforge.controlapi.security.OperatorIdentityResolver;
import dev.launchforge.domain.organization.OrganizationId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/audit")
public final class AuditController {
  private final OperatorIdentityResolver identityResolver;
  private final ControlPlaneService service;
  private final AuditRetentionService retentionService;

  public AuditController(
      OperatorIdentityResolver identityResolver,
      ControlPlaneService service,
      AuditRetentionService retentionService) {
    this.identityResolver = identityResolver;
    this.service = service;
    this.retentionService = retentionService;
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

  @GetMapping(value = "/export", produces = "text/csv")
  public ResponseEntity<String> export(
      Authentication authentication,
      @PathVariable UUID organizationId,
      @RequestParam(required = false) UUID projectId,
      @RequestParam(required = false) UUID environmentId,
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "200") int limit) {
    AuditQuery query = new AuditQuery(projectId, environmentId, actor, action, from, to, limit);
    List<AuditEvent> events =
        service.auditHistory(
            identityResolver.requireIdentity(authentication),
            new OrganizationId(organizationId),
            query);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=launchforge-audit.csv")
        .body(toCsv(events));
  }

  @PostMapping("/retention/preview")
  public AuditRetentionPreview previewRetention(
      Authentication authentication,
      @PathVariable UUID organizationId,
      @RequestBody AuditRetentionPreviewRequest request) {
    return retentionService.preview(
        identityResolver.requireIdentity(authentication),
        new OrganizationId(organizationId),
        request.deleteBefore(),
        request.limit());
  }

  @PostMapping("/retention/{previewId}/apply")
  public AuditRetentionResult applyRetention(
      Authentication authentication,
      @PathVariable UUID organizationId,
      @PathVariable UUID previewId,
      @RequestBody AuditRetentionApplyRequest request) {
    return retentionService.apply(
        identityResolver.requireIdentity(authentication),
        new OrganizationId(organizationId),
        previewId,
        request.expectedCandidateCount());
  }

  private static String toCsv(List<AuditEvent> events) {
    StringBuilder csv =
        new StringBuilder(
            "id,projectId,environmentId,actor,action,targetType,targetId,summary,reason,fromRevision,toRevision,correlationId,createdAt\r\n");
    for (AuditEvent event : events) {
      appendRow(
          csv,
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
    return csv.toString();
  }

  private static void appendRow(StringBuilder csv, Object... values) {
    for (int index = 0; index < values.length; index++) {
      if (index > 0) {
        csv.append(',');
      }
      csv.append(csvCell(values[index]));
    }
    csv.append("\r\n");
  }

  private static String csvCell(Object value) {
    String text = value == null ? "" : value.toString();
    if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
      text = "'" + text;
    }
    return '"' + text.replace("\"", "\"\"") + '"';
  }

  public record AuditRetentionPreviewRequest(Instant deleteBefore, int limit) {}

  public record AuditRetentionApplyRequest(int expectedCandidateCount) {}

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
