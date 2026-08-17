package dev.launchforge.configedge.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.launchforge.configedge.configuration.EdgeAbuseProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EdgeRateLimiterTest {
  @Test
  void independentlyLimitsTrustedKeysAndEndpointClassesWithLowCardinalityMetrics() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    EdgeRateLimiter limiter =
        new EdgeRateLimiter(
            null,
            new EdgeAbuseProperties(false, Duration.ofMinutes(1), 1, 1, 1, Duration.ofMinutes(1)),
            Clock.fixed(Instant.parse("2026-08-17T12:00:30Z"), ZoneOffset.UTC),
            registry);
    UUID key = UUID.randomUUID();

    assertTrue(limiter.check(EdgeRateLimiter.Policy.SNAPSHOT, key).permitted());
    assertFalse(limiter.check(EdgeRateLimiter.Policy.SNAPSHOT, key).permitted());
    assertTrue(limiter.check(EdgeRateLimiter.Policy.STREAM, key).permitted());
    assertTrue(limiter.check(EdgeRateLimiter.Policy.SNAPSHOT, UUID.randomUUID()).permitted());
    assertTrue(
        registry.getMeters().stream()
            .flatMap(meter -> meter.getId().getTags().stream())
            .noneMatch(tag -> tag.getValue().contains(key.toString())));
  }
}
