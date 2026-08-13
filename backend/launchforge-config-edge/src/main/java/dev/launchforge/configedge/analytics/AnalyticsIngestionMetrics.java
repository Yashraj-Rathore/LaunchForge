package dev.launchforge.configedge.analytics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "launchforge.analytics.ingestion.enabled", havingValue = "true")
public final class AnalyticsIngestionMetrics {
  private final Counter acceptedBatches;
  private final Counter acceptedEvents;
  private final Counter rejectedBatches;
  private final Counter shedBatches;
  private final Counter brokerFailures;

  public AnalyticsIngestionMetrics(MeterRegistry registry) {
    acceptedBatches =
        registry.counter("launchforge.analytics.ingestion", "outcome", "accepted_batch");
    acceptedEvents =
        registry.counter("launchforge.analytics.ingestion", "outcome", "accepted_event");
    rejectedBatches = registry.counter("launchforge.analytics.ingestion", "outcome", "rejected");
    shedBatches = registry.counter("launchforge.analytics.ingestion", "outcome", "shed");
    brokerFailures =
        registry.counter("launchforge.analytics.ingestion", "outcome", "broker_failure");
  }

  void accepted(int events) {
    acceptedBatches.increment();
    acceptedEvents.increment(events);
  }

  void rejected() {
    rejectedBatches.increment();
  }

  void shed() {
    shedBatches.increment();
  }

  void brokerFailure() {
    brokerFailures.increment();
  }
}
