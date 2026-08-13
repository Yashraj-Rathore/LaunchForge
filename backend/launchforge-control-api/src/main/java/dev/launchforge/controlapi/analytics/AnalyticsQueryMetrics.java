package dev.launchforge.controlapi.analytics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
final class AnalyticsQueryMetrics {
  private final MeterRegistry registry;
  private final Counter success;
  private final Counter failure;
  private final Counter shed;

  AnalyticsQueryMetrics(MeterRegistry registry) {
    this.registry = registry;
    success = registry.counter("launchforge.analytics.query", "outcome", "success");
    failure = registry.counter("launchforge.analytics.query", "outcome", "failure");
    shed = registry.counter("launchforge.analytics.query", "outcome", "shed");
  }

  Timer.Sample start() {
    return Timer.start(registry);
  }

  void success(Timer.Sample sample) {
    success.increment();
    sample.stop(registry.timer("launchforge.analytics.query.duration"));
  }

  void failure(Timer.Sample sample) {
    failure.increment();
    sample.stop(registry.timer("launchforge.analytics.query.duration"));
  }

  void shed() {
    shed.increment();
  }
}
