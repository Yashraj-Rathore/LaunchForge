package dev.launchforge.domain.controlplane;

public final class ControlPlaneRuleViolationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ControlPlaneRuleViolationException(String message) {
    super(message);
  }
}
