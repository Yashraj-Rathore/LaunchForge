package dev.launchforge.controlapi.observability;

import dev.launchforge.application.controlplane.ControlPlaneService;
import dev.launchforge.application.controlplane.RevisionDiagnostics;
import dev.launchforge.controlapi.security.OperatorIdentityResolver;
import dev.launchforge.domain.controlplane.EnvironmentId;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class RevisionDiagnosticsController {
  private final OperatorIdentityResolver identityResolver;
  private final ControlPlaneService service;
  private final RedisRevisionDiagnosticsReader redisReader;

  public RevisionDiagnosticsController(
      OperatorIdentityResolver identityResolver,
      ControlPlaneService service,
      RedisRevisionDiagnosticsReader redisReader) {
    this.identityResolver = identityResolver;
    this.service = service;
    this.redisReader = redisReader;
  }

  @GetMapping("/api/v1/environments/{environmentId}/diagnostics/revision")
  public Response revision(Authentication authentication, @PathVariable UUID environmentId) {
    RevisionDiagnostics database =
        service.revisionDiagnostics(
            identityResolver.requireIdentity(authentication), new EnvironmentId(environmentId));
    OptionalLong materialized = redisReader.revision(environmentId);
    Long redisRevision = materialized.isPresent() ? materialized.getAsLong() : null;
    long resolvableRevision = materialized.orElse(database.databaseRevision());
    String status =
        database.failedOutboxCount() > 0
            ? "FAILED"
            : database.pendingOutboxCount() > 0
                ? "PENDING"
                : redisRevision == null || redisRevision < database.databaseRevision()
                    ? "UNMATERIALIZED"
                    : "CURRENT";
    return new Response(
        environmentId,
        database.databaseRevision(),
        redisRevision,
        resolvableRevision,
        database.pendingOutboxCount(),
        database.failedOutboxCount(),
        database.oldestPendingAgeMillis(),
        status);
  }

  public record Response(
      UUID environmentId,
      long databaseRevision,
      Long redisRevision,
      long edgeResolvableRevision,
      long pendingOutboxCount,
      long failedOutboxCount,
      long oldestPendingAgeMillis,
      String status) {}
}
