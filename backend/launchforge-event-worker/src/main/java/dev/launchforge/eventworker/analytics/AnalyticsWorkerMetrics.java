package dev.launchforge.eventworker.analytics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "launchforge.analytics.worker.enabled", havingValue = "true")
public final class AnalyticsWorkerMetrics {
  private final MeterRegistry registry;
  private final AtomicInteger queued = new AtomicInteger();
  private final Counter enqueued;
  private final Counter stored;
  private final Counter dropped;
  private final Counter invalid;
  private final Counter storeFailures;

  public AnalyticsWorkerMetrics(MeterRegistry registry) {
    this.registry = registry;
    Gauge.builder("launchforge.analytics.queue.depth", queued, AtomicInteger::doubleValue)
        .description("Evaluation analytics rows awaiting ClickHouse insertion")
        .register(registry);
    enqueued = registry.counter("launchforge.analytics.worker", "outcome", "enqueued");
    stored = registry.counter("launchforge.analytics.worker", "outcome", "stored");
    dropped = registry.counter("launchforge.analytics.worker", "outcome", "dropped");
    invalid = registry.counter("launchforge.analytics.worker", "outcome", "invalid");
    storeFailures = registry.counter("launchforge.analytics.worker", "outcome", "store_failure");
  }

  void depth(int value) {
    queued.set(value);
  }

  void enqueued(int value) {
    enqueued.increment(value);
  }

  void stored(int value) {
    stored.increment(value);
  }

  Timer.Sample startInsert() {
    return Timer.start(registry);
  }

  void finishInsert(Timer.Sample sample, String outcome) {
    sample.stop(
        registry.timer("launchforge.analytics.clickhouse.insert.duration", "outcome", outcome));
  }

  void dropped(int value) {
    dropped.increment(value);
  }

  void invalid() {
    invalid.increment();
  }

  void storeFailure() {
    storeFailures.increment();
  }
}
