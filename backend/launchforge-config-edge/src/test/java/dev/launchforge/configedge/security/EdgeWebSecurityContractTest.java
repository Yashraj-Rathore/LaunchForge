package dev.launchforge.configedge.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.launchforge.configedge.configuration.AnalyticsIngestionProperties;
import dev.launchforge.configedge.configuration.EdgeAbuseProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class EdgeWebSecurityContractTest {
  @Test
  void hardenedHeadersIncludeHstsOnlyForHttps() {
    EdgeSecurityHeadersWebFilter filter = new EdgeSecurityHeadersWebFilter();
    MockServerWebExchange secure =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("https://edge.example/sdk/v1/snapshot"));
    filter.filter(secure, ignored -> Mono.empty()).block();

    assertEquals("nosniff", secure.getResponse().getHeaders().getFirst("X-Content-Type-Options"));
    assertEquals("no-referrer", secure.getResponse().getHeaders().getFirst("Referrer-Policy"));
    assertTrue(
        secure
            .getResponse()
            .getHeaders()
            .getFirst("Content-Security-Policy")
            .contains("default-src 'none'"));
    assertTrue(
        secure
            .getResponse()
            .getHeaders()
            .getFirst("Strict-Transport-Security")
            .contains("max-age=31536000"));

    MockServerWebExchange local =
        MockServerWebExchange.from(MockServerHttpRequest.get("http://localhost/sdk/v1/snapshot"));
    filter.filter(local, ignored -> Mono.empty()).block();
    assertNull(local.getResponse().getHeaders().getFirst("Strict-Transport-Security"));
  }

  @Test
  void edgeLoggerDoesNotEmitCredentialPathHeadersOrQueryValues() {
    String fakeSecret = "lf_client_FAKECLIENTKEYTHATMUSTNEVERBELOGGED";
    Logger logger = (Logger) LoggerFactory.getLogger(EdgePrivacySafeRequestLoggingWebFilter.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      MockServerWebExchange exchange =
          MockServerWebExchange.from(
              MockServerHttpRequest.get(
                      "https://edge.example/sdk/v1/client/"
                          + fakeSecret
                          + "/snapshot?subject=maya@example.test")
                  .header(HttpHeaders.AUTHORIZATION, "LF-SDK " + fakeSecret));
      new EdgePrivacySafeRequestLoggingWebFilter()
          .filter(exchange, ignored -> Mono.empty())
          .block();

      String logs =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertTrue(logs.contains("routeFamily=browser_snapshot"));
      assertFalse(logs.contains(fakeSecret));
      assertFalse(logs.contains("maya@example.test"));
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void analyticsBodyLimitRejectsOversizedRequestsBeforeControllerAllocation() {
    AnalyticsIngestionProperties properties =
        new AnalyticsIngestionProperties(
            true,
            "launchforge.analytics.evaluations.v1",
            1024,
            100,
            16,
            600,
            Duration.ofSeconds(2),
            Duration.ofHours(24),
            Duration.ofMinutes(5));
    EdgeRequestBodySizeWebFilter filter = new EdgeRequestBodySizeWebFilter(properties);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/events/v1/evaluations/batch")
                .header(HttpHeaders.CONTENT_LENGTH, "1025"));

    filter.filter(exchange, ignored -> Mono.empty()).block();

    assertEquals(413, exchange.getResponse().getStatusCode().value());
  }

  @Test
  void trustedKeyRateLimitReturnsStable429AndRetryAfter() {
    EdgeRateLimiter limiter =
        new EdgeRateLimiter(
            null,
            new EdgeAbuseProperties(false, Duration.ofMinutes(1), 1, 1, 1, Duration.ofMinutes(1)),
            Clock.fixed(Instant.parse("2026-08-17T12:00:30Z"), ZoneOffset.UTC),
            new SimpleMeterRegistry());
    EdgeRateLimitWebFilter filter = new EdgeRateLimitWebFilter(limiter);
    UUID keyId = UUID.randomUUID();
    MockServerWebExchange first =
        MockServerWebExchange.from(MockServerHttpRequest.get("/sdk/v1/snapshot"));
    first
        .getAttributes()
        .put(
            SdkAuthenticationWebFilter.SCOPE_ATTRIBUTE,
            new SdkCredentialScope(keyId, UUID.randomUUID()));
    filter.filter(first, ignored -> Mono.empty()).block();

    MockServerWebExchange second =
        MockServerWebExchange.from(MockServerHttpRequest.get("/sdk/v1/snapshot"));
    second
        .getAttributes()
        .put(
            SdkAuthenticationWebFilter.SCOPE_ATTRIBUTE,
            new SdkCredentialScope(keyId, UUID.randomUUID()));
    filter.filter(second, ignored -> Mono.empty()).block();

    assertEquals(429, second.getResponse().getStatusCode().value());
    assertTrue(
        Long.parseLong(second.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)) > 0);
  }
}
