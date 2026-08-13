package dev.launchforge.controlapi.analytics;

import dev.launchforge.application.controlplane.ControlPlaneService;
import dev.launchforge.controlapi.analytics.AnalyticsQueryGateway.AnalyticsBucket;
import dev.launchforge.controlapi.analytics.AnalyticsQueryGateway.BucketSize;
import dev.launchforge.controlapi.security.OperatorIdentityResolver;
import dev.launchforge.domain.controlplane.EnvironmentId;
import dev.launchforge.domain.controlplane.ResourceKey;
import java.time.Clock;
import java.time.Duration;
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
@RequestMapping("/api/v1/environments/{environmentId}/analytics/evaluations")
public final class AnalyticsController {
  private final OperatorIdentityResolver identityResolver;
  private final ControlPlaneService controlPlaneService;
  private final AnalyticsQueryGateway gateway;
  private final AnalyticsQueryProperties properties;
  private final Clock clock;

  public AnalyticsController(
      OperatorIdentityResolver identityResolver,
      ControlPlaneService controlPlaneService,
      AnalyticsQueryGateway gateway,
      AnalyticsQueryProperties properties,
      Clock clock) {
    this.identityResolver = identityResolver;
    this.controlPlaneService = controlPlaneService;
    this.gateway = gateway;
    this.properties = properties;
    this.clock = clock;
  }

  @GetMapping
  public AnalyticsResponse evaluations(
      Authentication authentication,
      @PathVariable UUID environmentId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) String flagKey,
      @RequestParam(required = false) String variationId,
      @RequestParam(defaultValue = "HOUR") BucketSize bucket,
      @RequestParam(defaultValue = "500") int limit) {
    Instant effectiveTo = to == null ? clock.instant() : to;
    Instant effectiveFrom = from == null ? effectiveTo.minus(Duration.ofHours(24)) : from;
    validate(effectiveFrom, effectiveTo, flagKey, variationId, limit);
    var scope =
        controlPlaneService.analyticsScope(
            identityResolver.requireIdentity(authentication), new EnvironmentId(environmentId));
    List<AnalyticsBucket> rows =
        gateway.query(scope, effectiveFrom, effectiveTo, flagKey, variationId, bucket, limit);
    return new AnalyticsResponse(
        effectiveFrom,
        effectiveTo,
        bucket,
        "Operational evaluation counts only; no experiment significance or causal claim.",
        rows);
  }

  private void validate(Instant from, Instant to, String flagKey, String variationId, int limit) {
    if (!from.isBefore(to) || Duration.between(from, to).compareTo(properties.maximumRange()) > 0) {
      throw new IllegalArgumentException("Analytics time range is invalid");
    }
    if (to.isAfter(clock.instant().plus(Duration.ofMinutes(5)))) {
      throw new IllegalArgumentException("Analytics range extends too far into the future");
    }
    if (flagKey != null) {
      new ResourceKey(flagKey);
    }
    if (variationId != null) {
      new ResourceKey(variationId);
    }
    if (limit < 1 || limit > properties.maximumRows()) {
      throw new IllegalArgumentException("Analytics row limit is invalid");
    }
  }

  public record AnalyticsResponse(
      Instant from,
      Instant to,
      BucketSize bucket,
      String interpretation,
      List<AnalyticsBucket> rows) {}
}
