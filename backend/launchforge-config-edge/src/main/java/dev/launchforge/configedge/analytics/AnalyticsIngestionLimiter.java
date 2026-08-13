package dev.launchforge.configedge.analytics;

import dev.launchforge.configedge.configuration.AnalyticsIngestionProperties;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "launchforge.analytics.ingestion.enabled", havingValue = "true")
final class AnalyticsIngestionLimiter {
  private final Semaphore concurrent;
  private final int perMinute;
  private final Clock clock;
  private final ConcurrentHashMap<UUID, Window> windows = new ConcurrentHashMap<>();

  AnalyticsIngestionLimiter(AnalyticsIngestionProperties properties, Clock clock) {
    concurrent = new Semaphore(properties.maximumConcurrentRequests());
    perMinute = properties.requestsPerKeyPerMinute();
    this.clock = clock;
  }

  Lease acquire(UUID keyId) {
    if (!concurrent.tryAcquire()) {
      throw new AnalyticsCapacityException("Analytics ingestion concurrency is exhausted");
    }
    boolean allowed = false;
    try {
      long minute = clock.instant().getEpochSecond() / 60;
      Window window =
          windows.compute(
              keyId,
              (ignored, current) ->
                  current == null || current.minute() != minute
                      ? new Window(minute, new AtomicInteger())
                      : current);
      allowed = window.requests().incrementAndGet() <= perMinute;
      if (!allowed) {
        throw new AnalyticsCapacityException("Analytics ingestion rate limit is exhausted");
      }
      if (windows.size() > 10_000) {
        windows.entrySet().removeIf(entry -> entry.getValue().minute() < minute - 1);
      }
      return concurrent::release;
    } finally {
      if (!allowed) {
        concurrent.release();
      }
    }
  }

  interface Lease extends AutoCloseable {
    @Override
    void close();
  }

  private record Window(long minute, AtomicInteger requests) {}
}
