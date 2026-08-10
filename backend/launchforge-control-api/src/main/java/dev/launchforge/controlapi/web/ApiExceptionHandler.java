package dev.launchforge.controlapi.web;

import dev.launchforge.application.controlplane.ControlPlaneConflictException;
import dev.launchforge.application.controlplane.ControlPlaneNotFoundException;
import dev.launchforge.application.controlplane.StaleWriteException;
import dev.launchforge.application.organization.OperationForbiddenException;
import dev.launchforge.application.organization.OrganizationConflictException;
import dev.launchforge.application.organization.OrganizationNotFoundException;
import dev.launchforge.domain.controlplane.ControlPlaneRuleViolationException;
import dev.launchforge.domain.organization.DomainRuleViolationException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public final class ApiExceptionHandler {
  @ExceptionHandler(OrganizationNotFoundException.class)
  ProblemDetail organizationNotFound() {
    return problem(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND", "Organization not found");
  }

  @ExceptionHandler(ControlPlaneNotFoundException.class)
  ProblemDetail controlPlaneNotFound() {
    return problem(HttpStatus.NOT_FOUND, "CONTROL_PLANE_RESOURCE_NOT_FOUND", "Resource not found");
  }

  @ExceptionHandler(OperationForbiddenException.class)
  ProblemDetail forbidden() {
    return problem(HttpStatus.FORBIDDEN, "OPERATION_FORBIDDEN", "Operation is not permitted");
  }

  @ExceptionHandler(OrganizationConflictException.class)
  ProblemDetail conflict(OrganizationConflictException exception) {
    return problem(HttpStatus.CONFLICT, "ORGANIZATION_CONFLICT", exception.getMessage());
  }

  @ExceptionHandler(StaleWriteException.class)
  ProblemDetail staleWrite() {
    return problem(HttpStatus.CONFLICT, "STALE_RESOURCE_VERSION", "Resource version is stale");
  }

  @ExceptionHandler(ControlPlaneConflictException.class)
  ProblemDetail controlPlaneConflict(ControlPlaneConflictException exception) {
    return problem(HttpStatus.CONFLICT, "CONTROL_PLANE_CONFLICT", exception.getMessage());
  }

  @ExceptionHandler(PreconditionRequiredException.class)
  ProblemDetail preconditionRequired() {
    return problem(
        HttpStatus.PRECONDITION_REQUIRED,
        "IF_MATCH_REQUIRED",
        "A valid If-Match resource version is required");
  }

  @ExceptionHandler({
    DomainRuleViolationException.class,
    ControlPlaneRuleViolationException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ProblemDetail invalidInput(Exception exception) {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed");
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("https://launchforge.dev/problems/" + code.toLowerCase()));
    problem.setTitle(status.getReasonPhrase());
    problem.setProperty("code", code);
    problem.setProperty("correlationId", UUID.randomUUID().toString());
    return problem;
  }
}
