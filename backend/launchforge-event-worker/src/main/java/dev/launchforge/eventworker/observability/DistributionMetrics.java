package dev.launchforge.eventworker.observability;

import dev.launchforge.eventworker.outbox.OutboxStats;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class DistributionMetrics {
  private final AtomicLong pendingCount = new AtomicLong();
  private final AtomicReference<Double> oldestPendingAge = new AtomicReference<>(0.0);
  private final Counter outboxPublished;
  private final Counter outboxRetries;
  private final Counter outboxFailed;
  private final Counter projectionAdvanced;
  private final Counter projectionIgnored;
  private final Counter projectionErrors;

  public DistributionMetrics(MeterRegistry registry) {
    Gauge.builder("launchforge.outbox.pending", pendingCount, AtomicLong::doubleValue)
        .description("Outbox rows awaiting a broker acknowledgement")
        .register(registry);
    Gauge.builder("launchforge.outbox.oldest.age.seconds", oldestPendingAge, AtomicReference::get)
        .description("Age of the oldest unpublished outbox row")
        .register(registry);
    outboxPublished = registry.counter("launchforge.outbox.publish", "outcome", "published");
    outboxRetries = registry.counter("launchforge.outbox.publish", "outcome", "retry");
    outboxFailed = registry.counter("launchforge.outbox.publish", "outcome", "failed");
    projectionAdvanced = registry.counter("launchforge.projection", "outcome", "advanced");
    projectionIgnored = registry.counter("launchforge.projection", "outcome", "ignored");
    projectionErrors = registry.counter("launchforge.projection", "outcome", "error");
  }

  public void recordOutboxStats(OutboxStats stats) {
    pendingCount.set(stats.pendingCount());
    oldestPendingAge.set(Math.max(0.0, stats.oldestPendingAgeSeconds()));
  }

  public void outboxPublished() {
    outboxPublished.increment();
  }

  public void outboxRetry() {
    outboxRetries.increment();
  }

  public void outboxFailed() {
    outboxFailed.increment();
  }

  public void projectionAdvanced() {
    projectionAdvanced.increment();
  }

  public void projectionIgnored() {
    projectionIgnored.increment();
  }

  public void projectionError() {
    projectionErrors.increment();
  }
}
