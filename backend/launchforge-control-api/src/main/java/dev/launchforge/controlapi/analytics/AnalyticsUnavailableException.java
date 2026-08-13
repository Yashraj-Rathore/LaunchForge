package dev.launchforge.controlapi.analytics;

public final class AnalyticsUnavailableException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public AnalyticsUnavailableException(String message) {
    super(message);
  }

  public AnalyticsUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
