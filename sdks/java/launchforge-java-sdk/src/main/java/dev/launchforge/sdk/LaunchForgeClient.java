package dev.launchforge.sdk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Thread-safe server SDK with local evaluation and an in-memory last-known-good snapshot. */
public final class LaunchForgeClient implements AutoCloseable {
  private static final String SNAPSHOT_PATH = "sdk/v1/snapshot";
  private static final String STREAM_PATH = "sdk/v1/stream";
  private static final String ANALYTICS_PATH = "events/v1/evaluations/batch";
  private static final int MAXIMUM_SSE_LINE_LENGTH = 4096;
  private static final int MAXIMUM_SSE_DATA_LENGTH = 16_384;
  private static final ObjectMapper EVENT_MAPPER = new ObjectMapper();
  private final String sdkKey;
  private final URI snapshotUri;
  private final URI streamUri;
  private final Duration requestTimeout;
  private final Duration minimumPollInterval;
  private final Duration maximumPollInterval;
  private final boolean streaming;
  private final Duration minimumStreamReconnectDelay;
  private final Duration maximumStreamReconnectDelay;
  private final HttpClient httpClient;
  private final ScheduledExecutorService scheduler;
  private final ExecutorService streamExecutor;
  private final AnalyticsDispatcher analytics;
  private final CountDownLatch closedSignal = new CountDownLatch(1);
  private final AtomicReference<ActiveSnapshot> active = new AtomicReference<>();
  private final AtomicReference<CompletableFuture<Boolean>> manualRefresh = new AtomicReference<>();
  private final AtomicBoolean refreshInProgress = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();

  private LaunchForgeClient(Builder builder) {
    sdkKey = builder.sdkKey;
    snapshotUri = snapshotUri(builder.baseUri);
    streamUri = endpointUri(builder.baseUri, STREAM_PATH);
    requestTimeout = builder.requestTimeout;
    minimumPollInterval = builder.minimumPollInterval;
    maximumPollInterval = builder.maximumPollInterval;
    streaming = builder.streaming;
    minimumStreamReconnectDelay = builder.minimumStreamReconnectDelay;
    maximumStreamReconnectDelay = builder.maximumStreamReconnectDelay;
    httpClient =
        HttpClient.newBuilder()
            .connectTimeout(builder.connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "launchforge-sdk-refresh");
              thread.setDaemon(true);
              return thread;
            });
    streamExecutor =
        Executors.newSingleThreadExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "launchforge-sdk-stream");
              thread.setDaemon(true);
              return thread;
            });
    analytics =
        builder.analyticsOptions == null
            ? null
            : new AnalyticsDispatcher(
                sdkKey,
                endpointUri(builder.baseUri, ANALYTICS_PATH),
                httpClient,
                builder.analyticsOptions);
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean boolVariation(String flagKey, EvaluationContext context, boolean defaultValue) {
    return boolVariationDetail(flagKey, context, defaultValue).value();
  }

  public EvaluationDetail<Boolean> boolVariationDetail(
      String flagKey, EvaluationContext context, boolean defaultValue) {
    ActiveSnapshot current = active.get();
    EvaluationDetail<Boolean> detail =
        current == null
            ? unavailable(defaultValue)
            : Evaluator.evaluateBoolean(current.snapshot(), flagKey, context, defaultValue);
    recordAnalytics(flagKey, detail);
    return detail;
  }

  public String stringVariation(String flagKey, EvaluationContext context, String defaultValue) {
    return stringVariationDetail(flagKey, context, defaultValue).value();
  }

  public EvaluationDetail<String> stringVariationDetail(
      String flagKey, EvaluationContext context, String defaultValue) {
    Objects.requireNonNull(defaultValue, "defaultValue");
    ActiveSnapshot current = active.get();
    EvaluationDetail<String> detail =
        current == null
            ? unavailable(defaultValue)
            : Evaluator.evaluateString(current.snapshot(), flagKey, context, defaultValue);
    recordAnalytics(flagKey, detail);
    return detail;
  }

  public double numberVariation(String flagKey, EvaluationContext context, double defaultValue) {
    return numberVariationDetail(flagKey, context, defaultValue).value();
  }

  public EvaluationDetail<Double> numberVariationDetail(
      String flagKey, EvaluationContext context, double defaultValue) {
    if (!Double.isFinite(defaultValue)) {
      throw new IllegalArgumentException("Default number must be finite binary64");
    }
    ActiveSnapshot current = active.get();
    EvaluationDetail<Double> detail =
        current == null
            ? unavailable(defaultValue == 0.0d ? 0.0d : defaultValue)
            : Evaluator.evaluateNumber(current.snapshot(), flagKey, context, defaultValue);
    recordAnalytics(flagKey, detail);
    return detail;
  }

  public JsonValue jsonVariation(
      String flagKey, EvaluationContext context, JsonValue defaultValue) {
    return jsonVariationDetail(flagKey, context, defaultValue).value();
  }

  public EvaluationDetail<JsonValue> jsonVariationDetail(
      String flagKey, EvaluationContext context, JsonValue defaultValue) {
    Objects.requireNonNull(defaultValue, "defaultValue");
    ActiveSnapshot current = active.get();
    EvaluationDetail<JsonValue> detail =
        current == null
            ? unavailable(defaultValue)
            : Evaluator.evaluateJson(current.snapshot(), flagKey, context, defaultValue);
    recordAnalytics(flagKey, detail);
    return detail;
  }

  /** Returns aggregate counters only; no context or flag values are retained in diagnostics. */
  public AnalyticsStatistics analyticsStatistics() {
    return analytics == null ? new AnalyticsStatistics(0, 0, 0, 0) : analytics.statistics();
  }

  public OptionalLong currentRevision() {
    ActiveSnapshot current = active.get();
    return current == null ? OptionalLong.empty() : OptionalLong.of(current.snapshot().revision());
  }

  /** Requests a refresh off the calling thread. Concurrent refreshes are coalesced. */
  public CompletableFuture<Boolean> refreshAsync() {
    if (closed.get()) {
      return CompletableFuture.completedFuture(false);
    }
    while (true) {
      CompletableFuture<Boolean> current = manualRefresh.get();
      if (current != null && !current.isDone()) {
        return current;
      }
      CompletableFuture<Boolean> replacement = new CompletableFuture<>();
      if (manualRefresh.compareAndSet(current, replacement)) {
        try {
          scheduler.execute(
              () -> {
                try {
                  replacement.complete(refreshOnce());
                } catch (RuntimeException exception) {
                  replacement.complete(false);
                } finally {
                  manualRefresh.compareAndSet(replacement, null);
                }
              });
        } catch (RejectedExecutionException exception) {
          replacement.complete(false);
          manualRefresh.compareAndSet(replacement, null);
        }
        return replacement;
      }
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      CompletableFuture<Boolean> pending = manualRefresh.getAndSet(null);
      if (pending != null) {
        pending.complete(false);
      }
      scheduler.shutdownNow();
      streamExecutor.shutdownNow();
      if (analytics != null) {
        analytics.close();
      }
      httpClient.shutdownNow();
      closedSignal.countDown();
    }
  }

  private void startPolling() {
    schedulePoll(randomPollDelayMillis());
  }

  private void schedulePoll(long delayMillis) {
    if (!closed.get()) {
      try {
        scheduler.schedule(
            () -> {
              try {
                refreshOnce();
              } finally {
                schedulePoll(randomPollDelayMillis());
              }
            },
            delayMillis,
            TimeUnit.MILLISECONDS);
      } catch (RejectedExecutionException exception) {
        if (!closed.get()) {
          throw exception;
        }
      }
    }
  }

  private void startStreaming() {
    if (streaming && !closed.get()) {
      streamExecutor.execute(this::streamLoop);
    }
  }

  private void streamLoop() {
    int consecutiveFailures = 0;
    while (!closed.get()) {
      refreshOnce();
      long startedAt = System.nanoTime();
      boolean opened = readStream();
      if (closed.get()) {
        return;
      }
      long connectedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
      if (opened && connectedMillis >= 30_000) {
        consecutiveFailures = 0;
      } else {
        consecutiveFailures = Math.min(consecutiveFailures + 1, 30);
      }
      long delay = randomStreamReconnectDelayMillis(consecutiveFailures);
      try {
        if (closedSignal.await(delay, TimeUnit.MILLISECONDS)) {
          return;
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private boolean readStream() {
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(streamUri)
              .header("Accept", "text/event-stream")
              .header("Authorization", "LF-SDK " + sdkKey)
              .GET();
      ActiveSnapshot current = active.get();
      if (current != null) {
        request.header("Last-Event-ID", Long.toString(current.snapshot().revision()));
      }
      HttpResponse<Stream<String>> response =
          httpClient.send(request.build(), HttpResponse.BodyHandlers.ofLines());
      if (response.statusCode() != 200
          || response
              .headers()
              .firstValue("Content-Type")
              .map(
                  value ->
                      !value.toLowerCase(java.util.Locale.ROOT).startsWith("text/event-stream"))
              .orElse(true)) {
        response.body().close();
        return false;
      }
      try (Stream<String> lines = response.body()) {
        consumeEvents(lines.iterator());
      }
      return true;
    } catch (IOException exception) {
      return false;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private void consumeEvents(Iterator<String> lines) {
    String event = "message";
    StringBuilder data = new StringBuilder();
    while (!closed.get() && lines.hasNext()) {
      String line = lines.next();
      if (line.length() > MAXIMUM_SSE_LINE_LENGTH) {
        throw new IllegalArgumentException("SSE line exceeds its bound");
      }
      if (line.isEmpty()) {
        dispatchEvent(event, data.toString());
        event = "message";
        data.setLength(0);
      } else if (!line.startsWith(":")) {
        int separator = line.indexOf(':');
        String field = separator < 0 ? line : line.substring(0, separator);
        String value = separator < 0 ? "" : line.substring(separator + 1);
        if (value.startsWith(" ")) {
          value = value.substring(1);
        }
        if ("event".equals(field)) {
          event = value;
        } else if ("data".equals(field)) {
          if (!data.isEmpty()) {
            data.append('\n');
          }
          data.append(value);
          if (data.length() > MAXIMUM_SSE_DATA_LENGTH) {
            throw new IllegalArgumentException("SSE event exceeds its bound");
          }
        }
      }
    }
  }

  private void dispatchEvent(String event, String data) {
    if (!"revision".equals(event) || data.isEmpty()) {
      return;
    }
    try {
      JsonNode root = EVENT_MAPPER.readTree(data.getBytes(StandardCharsets.UTF_8));
      JsonNode revisionNode = root == null ? null : root.get("revision");
      if (root == null
          || !root.isObject()
          || revisionNode == null
          || !revisionNode.isIntegralNumber()
          || !revisionNode.canConvertToLong()) {
        return;
      }
      long notifiedRevision = revisionNode.longValue();
      OptionalLong current = currentRevision();
      if (notifiedRevision > 0 && (current.isEmpty() || notifiedRevision > current.getAsLong())) {
        refreshAsync();
      }
    } catch (JacksonException exception) {
      // Malformed revision hints are non-authoritative and are ignored.
    }
  }

  private boolean refreshOnce() {
    if (closed.get() || !refreshInProgress.compareAndSet(false, true)) {
      return false;
    }
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(snapshotUri)
              .timeout(requestTimeout)
              .header("Accept", "application/json")
              .header("Authorization", "LF-SDK " + sdkKey)
              .GET();
      ActiveSnapshot current = active.get();
      if (current != null) {
        request.header("If-None-Match", current.etag());
      }
      HttpResponse<InputStream> response =
          httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
      try (InputStream body = response.body()) {
        if (response.statusCode() == 304) {
          return current != null;
        }
        if (response.statusCode() != 200) {
          return false;
        }
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (contentLength > SnapshotParser.MAX_SNAPSHOT_BYTES) {
          return false;
        }
        byte[] bytes = readBounded(body);
        CompiledSnapshot candidate = SnapshotParser.parse(bytes);
        String etag =
            response
                .headers()
                .firstValue("ETag")
                .filter(LaunchForgeClient::validEtag)
                .orElseGet(() -> etagFor(candidate));
        return activate(candidate, etag);
      }
    } catch (IOException exception) {
      return false;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IllegalArgumentException exception) {
      return false;
    } finally {
      refreshInProgress.set(false);
    }
  }

  private boolean activate(CompiledSnapshot candidate, String etag) {
    if (closed.get()) {
      return false;
    }
    ActiveSnapshot replacement = new ActiveSnapshot(candidate, etag);
    while (true) {
      ActiveSnapshot current = active.get();
      if (current != null) {
        long currentRevision = current.snapshot().revision();
        if (candidate.revision() < currentRevision) {
          return false;
        }
        if (candidate.revision() == currentRevision) {
          return candidate.checksum().equals(current.snapshot().checksum());
        }
      }
      if (active.compareAndSet(current, replacement)) {
        return true;
      }
    }
  }

  long randomPollDelayMillis() {
    long minimum = minimumPollInterval.toMillis();
    long maximum = maximumPollInterval.toMillis();
    return minimum == maximum
        ? minimum
        : ThreadLocalRandom.current().nextLong(minimum, maximum + 1);
  }

  long randomStreamReconnectDelayMillis(int consecutiveFailures) {
    int exponent = Math.max(0, Math.min(consecutiveFailures - 1, 30));
    long minimum = minimumStreamReconnectDelay.toMillis();
    long maximum = maximumStreamReconnectDelay.toMillis();
    long exponential;
    try {
      exponential = Math.multiplyExact(minimum, 1L << exponent);
    } catch (ArithmeticException exception) {
      exponential = maximum;
    }
    long cap = Math.min(maximum, exponential);
    long floor = Math.max(minimum, cap / 2);
    return floor == cap ? cap : ThreadLocalRandom.current().nextLong(floor, cap + 1);
  }

  private static byte[] readBounded(InputStream input) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int total = 0;
    int read;
    while ((read = input.read(buffer)) >= 0) {
      total += read;
      if (total > SnapshotParser.MAX_SNAPSHOT_BYTES) {
        throw new IOException("Snapshot response exceeds its bound");
      }
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }

  private static URI snapshotUri(URI baseUri) {
    return endpointUri(baseUri, SNAPSHOT_PATH);
  }

  private static URI endpointUri(URI baseUri, String path) {
    Objects.requireNonNull(baseUri, "baseUri");
    String scheme = baseUri.getScheme();
    if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
        || !baseUri.isAbsolute()
        || baseUri.getHost() == null
        || baseUri.getRawUserInfo() != null
        || baseUri.getRawQuery() != null
        || baseUri.getRawFragment() != null) {
      throw new IllegalArgumentException("Base URI must be an absolute HTTP(S) origin or path");
    }
    String value = baseUri.toString();
    return URI.create((value.endsWith("/") ? value : value + '/') + path);
  }

  private static boolean validEtag(String value) {
    return value.length() <= 256 && value.indexOf('\r') < 0 && value.indexOf('\n') < 0;
  }

  private static String etagFor(CompiledSnapshot snapshot) {
    return '"' + "revision:" + snapshot.revision() + ':' + snapshot.checksum() + '"';
  }

  private static <T> EvaluationDetail<T> unavailable(T defaultValue) {
    return new EvaluationDetail<>(
        Objects.requireNonNull(defaultValue, "defaultValue"),
        Optional.empty(),
        EvaluationReason.SNAPSHOT_UNAVAILABLE,
        Optional.empty(),
        OptionalLong.empty(),
        OptionalInt.empty(),
        Optional.of(EvaluationErrorKind.SNAPSHOT_UNAVAILABLE));
  }

  private void recordAnalytics(String flagKey, EvaluationDetail<?> detail) {
    if (analytics != null) {
      analytics.record(flagKey, detail);
    }
  }

  private record ActiveSnapshot(CompiledSnapshot snapshot, String etag) {}

  public static final class Builder {
    private String sdkKey;
    private URI baseUri;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration requestTimeout = Duration.ofSeconds(5);
    private Duration minimumPollInterval = Duration.ofSeconds(25);
    private Duration maximumPollInterval = Duration.ofSeconds(35);
    private boolean streaming;
    private Duration minimumStreamReconnectDelay = Duration.ofMillis(500);
    private Duration maximumStreamReconnectDelay = Duration.ofSeconds(30);
    private Duration blockingBootstrapTimeout;
    private AnalyticsOptions analyticsOptions;

    private Builder() {}

    public Builder sdkKey(String value) {
      sdkKey = requireSdkKey(value);
      return this;
    }

    public Builder baseUri(URI value) {
      baseUri = Objects.requireNonNull(value, "baseUri");
      return this;
    }

    public Builder connectTimeout(Duration value) {
      connectTimeout = requireDuration(value, "connectTimeout");
      return this;
    }

    public Builder requestTimeout(Duration value) {
      requestTimeout = requireDuration(value, "requestTimeout");
      return this;
    }

    public Builder pollingInterval(Duration minimum, Duration maximum) {
      minimumPollInterval = requireDuration(minimum, "minimumPollInterval");
      maximumPollInterval = requireDuration(maximum, "maximumPollInterval");
      if (maximumPollInterval.compareTo(minimumPollInterval) < 0) {
        throw new IllegalArgumentException("Maximum poll interval must not be below minimum");
      }
      return this;
    }

    /** Enables revision-notification streaming while retaining conditional polling fallback. */
    public Builder streaming(boolean enabled) {
      streaming = enabled;
      return this;
    }

    public Builder streamReconnectBackoff(Duration minimum, Duration maximum) {
      minimumStreamReconnectDelay = requireDuration(minimum, "minimumStreamReconnectDelay");
      maximumStreamReconnectDelay = requireDuration(maximum, "maximumStreamReconnectDelay");
      if (maximumStreamReconnectDelay.compareTo(minimumStreamReconnectDelay) < 0) {
        throw new IllegalArgumentException(
            "Maximum stream reconnect delay must not be below minimum");
      }
      return this;
    }

    /** Enables explicit blocking bootstrap; non-blocking bootstrap is the default. */
    public Builder blockingBootstrap(Duration timeout) {
      blockingBootstrapTimeout = requireDuration(timeout, "blockingBootstrapTimeout");
      return this;
    }

    /** Explicitly opts into best-effort analytics. Evaluation context is never transported. */
    public Builder analytics(AnalyticsOptions options) {
      analyticsOptions = Objects.requireNonNull(options, "analyticsOptions");
      return this;
    }

    public LaunchForgeClient build() {
      requireSdkKey(sdkKey);
      Objects.requireNonNull(baseUri, "baseUri");
      LaunchForgeClient client = new LaunchForgeClient(this);
      if (blockingBootstrapTimeout == null) {
        client.schedulePoll(0);
        client.startStreaming();
        return client;
      }
      CompletableFuture<Boolean> bootstrap =
          CompletableFuture.supplyAsync(client::refreshOnce, client.scheduler);
      try {
        if (!bootstrap.get(blockingBootstrapTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
          throw new BootstrapException("No valid initial LaunchForge snapshot was available");
        }
        client.startPolling();
        client.startStreaming();
        return client;
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        client.close();
        throw new BootstrapException("LaunchForge bootstrap was interrupted", exception);
      } catch (ExecutionException | TimeoutException exception) {
        bootstrap.cancel(true);
        client.close();
        throw new BootstrapException(
            "LaunchForge bootstrap did not complete within its timeout", exception);
      } catch (BootstrapException exception) {
        client.close();
        throw exception;
      }
    }

    private static String requireSdkKey(String value) {
      if (value == null
          || value.isBlank()
          || value.length() > 4096
          || value.indexOf('\r') >= 0
          || value.indexOf('\n') >= 0) {
        throw new IllegalArgumentException("SDK key is absent or invalid");
      }
      return value;
    }

    private static Duration requireDuration(Duration value, String label) {
      Objects.requireNonNull(value, label);
      if (value.toMillis() < 1 || value.compareTo(Duration.ofMinutes(5)) > 0) {
        throw new IllegalArgumentException(
            label + " must be between one millisecond and five minutes");
      }
      return value;
    }
  }
}
