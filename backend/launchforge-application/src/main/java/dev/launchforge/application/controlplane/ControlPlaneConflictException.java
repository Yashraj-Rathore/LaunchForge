package dev.launchforge.application.controlplane;

public final class ControlPlaneConflictException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ControlPlaneConflictException(String message) {
    super(message);
  }
}
