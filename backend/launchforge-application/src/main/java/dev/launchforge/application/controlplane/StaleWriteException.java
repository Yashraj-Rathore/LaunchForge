package dev.launchforge.application.controlplane;

public final class StaleWriteException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public StaleWriteException() {
    super("Resource version does not match");
  }
}
