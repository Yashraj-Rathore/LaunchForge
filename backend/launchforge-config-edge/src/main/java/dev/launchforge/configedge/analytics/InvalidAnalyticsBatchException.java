package dev.launchforge.configedge.analytics;

public final class InvalidAnalyticsBatchException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public InvalidAnalyticsBatchException(String message) {
    super(message);
  }

  public InvalidAnalyticsBatchException(String message, Throwable cause) {
    super(message, cause);
  }
}
