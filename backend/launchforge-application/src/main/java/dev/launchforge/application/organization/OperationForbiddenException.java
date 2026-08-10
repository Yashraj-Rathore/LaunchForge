package dev.launchforge.application.organization;

public final class OperationForbiddenException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public OperationForbiddenException() {
    super("The authenticated operator is not permitted to perform this operation");
  }
}
