package dev.launchforge.application.controlplane;

public final class ControlPlaneNotFoundException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ControlPlaneNotFoundException() {
    super("Control-plane resource was not found");
  }
}
