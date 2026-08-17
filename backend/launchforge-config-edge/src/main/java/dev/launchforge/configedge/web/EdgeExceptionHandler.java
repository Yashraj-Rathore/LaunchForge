package dev.launchforge.configedge.web;

import dev.launchforge.configedge.analytics.AnalyticsCapacityException;
import dev.launchforge.configedge.analytics.AnalyticsUnavailableException;
import dev.launchforge.configedge.analytics.InvalidAnalyticsBatchException;
import dev.launchforge.configedge.security.EdgeCorrelationWebFilter;
import dev.launchforge.configedge.snapshot.SnapshotUnavailableException;
import dev.launchforge.configedge.stream.ConnectionLimitExceededException;
import java.net.URI;
import java.util.Locale;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

@RestControllerAdvice
public final class EdgeExceptionHandler {
  @ExceptionHandler(SnapshotUnavailableException.class)
  ProblemDetail snapshotUnavailable(ServerWebExchange exchange) {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "SNAPSHOT_UNAVAILABLE",
        "No safe published snapshot is available",
        exchange);
  }

  @ExceptionHandler(ConnectionLimitExceededException.class)
  ProblemDetail connectionLimit(ServerWebExchange exchange) {
    return problem(
        HttpStatus.TOO_MANY_REQUESTS,
        "STREAM_CONNECTION_LIMIT",
        "The authenticated stream connection limit was reached",
        exchange);
  }

  @ExceptionHandler(InvalidAnalyticsBatchException.class)
  ProblemDetail invalidAnalytics(ServerWebExchange exchange) {
    return problem(
        HttpStatus.BAD_REQUEST, "ANALYTICS_BATCH_INVALID", "Analytics batch is invalid", exchange);
  }

  @ExceptionHandler(AnalyticsCapacityException.class)
  ProblemDetail analyticsCapacity(ServerWebExchange exchange) {
    return problem(
        HttpStatus.TOO_MANY_REQUESTS,
        "ANALYTICS_CAPACITY_EXHAUSTED",
        "Analytics ingestion capacity is temporarily exhausted",
        exchange);
  }

  @ExceptionHandler(AnalyticsUnavailableException.class)
  ProblemDetail analyticsUnavailable(ServerWebExchange exchange) {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "ANALYTICS_UNAVAILABLE",
        "Optional analytics ingestion is temporarily unavailable",
        exchange);
  }

  @ExceptionHandler(DataBufferLimitException.class)
  ProblemDetail bodyTooLarge(ServerWebExchange exchange) {
    return problem(
        HttpStatus.CONTENT_TOO_LARGE,
        "ANALYTICS_BODY_TOO_LARGE",
        "The analytics request body exceeds the version-one limit",
        exchange);
  }

  private static ProblemDetail problem(
      HttpStatus status, String code, String detail, ServerWebExchange exchange) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(
        URI.create("https://launchforge.dev/problems/" + code.toLowerCase(Locale.ROOT)));
    problem.setTitle(status.getReasonPhrase());
    problem.setProperty("code", code);
    problem.setProperty("correlationId", EdgeCorrelationWebFilter.get(exchange));
    return problem;
  }
}
