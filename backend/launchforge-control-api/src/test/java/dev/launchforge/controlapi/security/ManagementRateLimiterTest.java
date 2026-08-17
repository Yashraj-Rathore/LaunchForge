package dev.launchforge.controlapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ManagementRateLimiterTest {
  @Test
  void partitionsPoliciesAndNeverUsesIdentityMaterialAsAMetricTag() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    String sensitivePartition = "203.0.113.42:owner-subject";
    ManagementRateLimiter limiter =
        new ManagementRateLimiter(
            (StringRedisTemplate) null,
            new ControlPlaneAbuseProperties(false, Duration.ofMinutes(1), 1, 1, 1, 1, 1024),
            Clock.fixed(Instant.parse("2026-08-17T12:00:30Z"), ZoneOffset.UTC),
            registry);

    assertTrue(limiter.check(ManagementRateLimiter.Policy.LOGIN, sensitivePartition).permitted());
    ManagementRateLimiter.Decision rejected =
        limiter.check(ManagementRateLimiter.Policy.LOGIN, sensitivePartition);
    assertFalse(rejected.permitted());
    assertTrue(rejected.retryAfterSeconds() > 0);
    assertTrue(
        limiter
            .check(ManagementRateLimiter.Policy.MANAGEMENT_READ, sensitivePartition)
            .permitted());
    assertTrue(limiter.check(ManagementRateLimiter.Policy.LOGIN, "different-client").permitted());
    assertTrue(
        registry.getMeters().stream()
            .flatMap(meter -> meter.getId().getTags().stream())
            .noneMatch(tag -> tag.getValue().contains(sensitivePartition)));
  }

  @Test
  void filterReturnsStable429ContractAndRetryAfter() throws Exception {
    ManagementRateLimiter limiter =
        new ManagementRateLimiter(
            (StringRedisTemplate) null,
            new ControlPlaneAbuseProperties(false, Duration.ofMinutes(1), 1, 1, 1, 1, 1024),
            Clock.fixed(Instant.parse("2026-08-17T12:00:30Z"), ZoneOffset.UTC),
            new SimpleMeterRegistry());
    ManagementRateLimitFilter filter = new ManagementRateLimitFilter(limiter);
    MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/v1/projects");
    first.setRemoteAddr("203.0.113.7");
    filter.doFilter(first, new MockHttpServletResponse(), (request, response) -> {});

    MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/v1/projects");
    second.setRemoteAddr("203.0.113.7");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(second, response, (request, ignored) -> response.setStatus(204));

    assertEquals(429, response.getStatus());
    assertTrue(Long.parseLong(response.getHeader("Retry-After")) > 0);
    assertTrue(response.getContentAsString().contains("MANAGEMENT_RATE_LIMITED"));
  }
}
