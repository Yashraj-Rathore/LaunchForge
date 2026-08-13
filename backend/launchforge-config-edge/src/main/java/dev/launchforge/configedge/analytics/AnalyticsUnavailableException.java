package dev.launchforge.configedge.analytics;

public final class AnalyticsUnavailableException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public AnalyticsUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  public AnalyticsUnavailableException(String message) {
    super(message);
  }
}
