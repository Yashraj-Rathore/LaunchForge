package dev.launchforge.application.organization;

public final class OrganizationConflictException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public OrganizationConflictException(String message) {
    super(message);
  }
}
