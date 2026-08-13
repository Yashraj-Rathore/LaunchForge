package dev.launchforge.sdk;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

final class AnalyticsDispatcher implements AutoCloseable {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final String sdkKey;
  private final URI endpoint;
  private final HttpClient httpClient;
  private final AnalyticsOptions options;
  private final ArrayBlockingQueue<Event> queue;
  private final ScheduledExecutorService executor;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final LongAdder queued = new LongAdder();
  private final LongAdder sent = new LongAdder();
  private final LongAdder dropped = new LongAdder();
  private final LongAdder failedBatches = new LongAdder();

  AnalyticsDispatcher(
      String sdkKey, URI endpoint, HttpClient httpClient, AnalyticsOptions options) {
    this.sdkKey = sdkKey;
    this.endpoint = endpoint;
    this.httpClient = httpClient;
    this.options = options;
    queue = new ArrayBlockingQueue<>(options.queueCapacity());
    executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "launchforge-sdk-analytics");
              thread.setDaemon(true);
              return thread;
            });
    executor.scheduleWithFixedDelay(
        this::flushSafely,
        options.flushInterval().toMillis(),
        options.flushInterval().toMillis(),
        TimeUnit.MILLISECONDS);
  }

  void record(String flagKey, EvaluationDetail<?> detail) {
    if (closed.get() || detail.snapshotRevision().isEmpty()) {
      return;
    }
    Event event =
        new Event(
            UUID.randomUUID(),
            Instant.now(),
            flagKey,
            detail.variationId().orElse(null),
            detail.reason().name(),
            detail.snapshotRevision().orElseThrow());
    if (queue.offer(event)) {
      queued.increment();
      if (queue.size() >= options.batchSize()) {
        try {
          executor.execute(this::flushSafely);
        } catch (RejectedExecutionException exception) {
          dropQueued();
        }
      }
    } else {
      dropped.increment();
    }
  }

  AnalyticsStatistics statistics() {
    return new AnalyticsStatistics(queued.sum(), sent.sum(), dropped.sum(), failedBatches.sum());
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      executor.shutdown();
      flushSafely();
      executor.shutdownNow();
    }
  }

  private void flushSafely() {
    List<Event> batch = new ArrayList<>(options.batchSize());
    queue.drainTo(batch, options.batchSize());
    if (batch.isEmpty()) {
      return;
    }
    try {
      String json = encode(batch);
      HttpRequest request =
          HttpRequest.newBuilder(endpoint)
              .timeout(options.requestTimeout())
              .header("Accept", "application/json")
              .header("Authorization", "LF-SDK " + sdkKey)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
              .build();
      HttpResponse<Void> response =
          httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        sent.add(batch.size());
      } else {
        failedBatches.increment();
        dropped.add(batch.size());
      }
    } catch (IOException exception) {
      failedBatches.increment();
      dropped.add(batch.size());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      failedBatches.increment();
      dropped.add(batch.size());
    } catch (JacksonException exception) {
      failedBatches.increment();
      dropped.add(batch.size());
    } catch (RuntimeException exception) {
      failedBatches.increment();
      dropped.add(batch.size());
    }
  }

  private static String encode(List<Event> batch) throws JacksonException {
    List<Map<String, Object>> events =
        batch.stream()
            .map(
                event -> {
                  Map<String, Object> value = new LinkedHashMap<>();
                  value.put("eventId", event.eventId());
                  value.put("occurredAt", event.occurredAt().toString());
                  value.put("flagKey", event.flagKey());
                  value.put("variationId", event.variationId());
                  value.put("reason", event.reason());
                  value.put("revision", event.revision());
                  return value;
                })
            .toList();
    return MAPPER.writeValueAsString(
        Map.of("eventType", "analytics.evaluation-batch.v1", "schemaVersion", 1, "events", events));
  }

  private void dropQueued() {
    int remaining = queue.size();
    queue.clear();
    dropped.add(remaining);
  }

  private record Event(
      UUID eventId,
      Instant occurredAt,
      String flagKey,
      String variationId,
      String reason,
      long revision) {}
}
