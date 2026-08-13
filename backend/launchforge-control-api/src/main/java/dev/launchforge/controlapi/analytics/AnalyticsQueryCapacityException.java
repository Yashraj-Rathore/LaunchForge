package dev.launchforge.controlapi.analytics;

public final class AnalyticsQueryCapacityException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public AnalyticsQueryCapacityException() {
    super("Analytics query capacity is exhausted");
  }
}
