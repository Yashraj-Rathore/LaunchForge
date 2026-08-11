package dev.launchforge.configedge.web;

import dev.launchforge.configedge.snapshot.SnapshotUnavailableException;
import dev.launchforge.configedge.stream.ConnectionLimitExceededException;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class EdgeExceptionHandler {
  @ExceptionHandler(SnapshotUnavailableException.class)
  ProblemDetail snapshotUnavailable() {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "SNAPSHOT_UNAVAILABLE",
        "No safe published snapshot is available");
  }

  @ExceptionHandler(ConnectionLimitExceededException.class)
  ProblemDetail connectionLimit() {
    return problem(
        HttpStatus.TOO_MANY_REQUESTS,
        "STREAM_CONNECTION_LIMIT",
        "The authenticated stream connection limit was reached");
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(
        URI.create("https://launchforge.dev/problems/" + code.toLowerCase(Locale.ROOT)));
    problem.setTitle(status.getReasonPhrase());
    problem.setProperty("code", code);
    problem.setProperty("correlationId", UUID.randomUUID().toString());
    return problem;
  }
}
