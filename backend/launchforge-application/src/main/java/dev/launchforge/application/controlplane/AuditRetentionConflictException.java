package dev.launchforge.application.controlplane;

public final class AuditRetentionConflictException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public AuditRetentionConflictException(String message) {
    super(message);
  }
}
