package dev.launchforge.controlapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestPrivacyContractTest {
  private static final ControlPlaneAbuseProperties PROPERTIES =
      new ControlPlaneAbuseProperties(false, Duration.ofMinutes(1), 10, 10, 10, 10, 1024);

  @Test
  void requestLoggerUsesOnlyBoundedFieldsAndNeverEmitsSecretsOrRawContext() throws Exception {
    String fakeSecret = "lf_srv_FAKELOOKUP_FAKESECRETTHATMUSTNEVERBELOGGED";
    Logger logger = (Logger) LoggerFactory.getLogger(PrivacySafeRequestLoggingFilter.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      MockHttpServletRequest request =
          new MockHttpServletRequest(
              "POST", "/api/v1/projects/" + fakeSecret + "/flags?email=maya@example.test");
      request.addHeader("Authorization", "Bearer " + fakeSecret);
      request.setContent(("{\"context\":\"" + fakeSecret + "\"}").getBytes(StandardCharsets.UTF_8));
      MockHttpServletResponse response = new MockHttpServletResponse();

      new PrivacySafeRequestLoggingFilter()
          .doFilter(
              request, response, (ignoredRequest, ignoredResponse) -> response.setStatus(202));

      String logs =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertTrue(logs.contains("routeFamily=management"));
      assertTrue(logs.contains("status=202"));
      assertFalse(logs.contains(fakeSecret));
      assertFalse(logs.contains("maya@example.test"));
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void bodyLimiterRejectsDeclaredAndChunkedOversizeBodiesAndPreservesAcceptedBody()
      throws Exception {
    RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(PROPERTIES);
    MockHttpServletRequest declared = new MockHttpServletRequest("POST", "/api/v1/projects");
    declared.setContent(new byte[1025]);
    MockHttpServletResponse declaredResponse = new MockHttpServletResponse();
    filter.doFilter(declared, declaredResponse, (request, response) -> {});
    assertEquals(413, declaredResponse.getStatus());
    assertTrue(declaredResponse.getContentAsString().contains("MANAGEMENT_BODY_TOO_LARGE"));

    MockHttpServletRequest accepted = new MockHttpServletRequest("PATCH", "/api/v1/flags/one");
    byte[] body = "{\"name\":\"Safe\"}".getBytes(StandardCharsets.UTF_8);
    accepted.setContent(body);
    MockHttpServletResponse acceptedResponse = new MockHttpServletResponse();
    AtomicReference<String> observed = new AtomicReference<>();
    filter.doFilter(
        accepted,
        acceptedResponse,
        (request, response) ->
            observed.set(
                new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));
    assertEquals(new String(body, StandardCharsets.UTF_8), observed.get());
  }

  @Test
  void correlationIdIsBoundedEchoedAndReusedByProblemResponses() throws Exception {
    CorrelationIdFilter filter = new CorrelationIdFilter();
    MockHttpServletRequest accepted = new MockHttpServletRequest("GET", "/api/v1/missing");
    accepted.addHeader(CorrelationIdFilter.HEADER_NAME, "caller-123");
    MockHttpServletResponse acceptedResponse = new MockHttpServletResponse();
    filter.doFilter(
        accepted,
        acceptedResponse,
        (request, response) ->
            SecurityProblemWriter.write(
                accepted,
                acceptedResponse,
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Authentication is required"));
    assertEquals("caller-123", acceptedResponse.getHeader(CorrelationIdFilter.HEADER_NAME));
    assertTrue(acceptedResponse.getContentAsString().contains("\"correlationId\":\"caller-123\""));

    MockHttpServletRequest unsafe = new MockHttpServletRequest("GET", "/api/v1/missing");
    unsafe.addHeader(CorrelationIdFilter.HEADER_NAME, "contains spaces and is rejected");
    MockHttpServletResponse unsafeResponse = new MockHttpServletResponse();
    filter.doFilter(unsafe, unsafeResponse, (request, response) -> {});
    String generated = unsafeResponse.getHeader(CorrelationIdFilter.HEADER_NAME);
    assertTrue(generated.matches("[0-9a-f-]{36}"));
  }
}
