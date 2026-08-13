package dev.launchforge.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class LaunchForgeClientTest {
  private static final EvaluationContext CONTEXT = EvaluationContext.builder("subject-1").build();

  @Test
  void nonBlockingBootstrapReturnsBoundedUnavailableDetail() {
    try (LaunchForgeClient client =
        LaunchForgeClient.builder()
            .sdkKey("sdk_test_nonblocking")
            .baseUri(URI.create("http://127.0.0.1:1"))
            .connectTimeout(Duration.ofMillis(50))
            .requestTimeout(Duration.ofMillis(50))
            .pollingInterval(Duration.ofMinutes(5), Duration.ofMinutes(5))
            .build()) {
      EvaluationDetail<Boolean> detail = client.boolVariationDetail("release", CONTEXT, false);

      assertFalse(detail.value());
      assertEquals(EvaluationReason.SNAPSHOT_UNAVAILABLE, detail.reason());
      assertTrue(detail.snapshotRevision().isEmpty());
      assertEquals(EvaluationErrorKind.SNAPSHOT_UNAVAILABLE, detail.errorKind().orElseThrow());
    }
  }

  @Test
  void blockingBootstrapAuthenticatesAndUsesConditionalGetWithoutReplacingOn304() throws Exception {
    AtomicReference<Response> response =
        new AtomicReference<>(new Response(200, snapshot(1, true), "\"revision:1:first\""));
    AtomicReference<String> authorization = new AtomicReference<>();
    AtomicReference<String> ifNoneMatch = new AtomicReference<>();
    try (TestServer server = new TestServer(response, authorization, ifNoneMatch);
        LaunchForgeClient client = blockingClient(server.uri())) {
      assertTrue(client.boolVariation("release", CONTEXT, false));
      assertEquals(1, client.currentRevision().orElseThrow());
      assertEquals("LF-SDK sdk_test_secret", authorization.get());

      response.set(new Response(304, "", null));
      assertTrue(client.refreshAsync().get());

      assertEquals("\"revision:1:first\"", ifNoneMatch.get());
      assertEquals(1, client.currentRevision().orElseThrow());
      assertTrue(client.boolVariation("release", CONTEXT, false));
    }
  }

  @Test
  @SuppressWarnings("try")
  void invalidStaleAndTransientResponsesRetainLastKnownGood() throws Exception {
    AtomicReference<Response> response =
        new AtomicReference<>(new Response(200, snapshot(5, true), "\"revision:5:valid\""));
    try (TestServer server =
            new TestServer(response, new AtomicReference<>(), new AtomicReference<>());
        LaunchForgeClient client = blockingClient(server.uri())) {
      String invalid = snapshot(6, false).replace("\"revision\":6", "\"revision\":7");
      response.set(new Response(200, invalid, "\"revision:7:invalid\""));
      assertFalse(client.refreshAsync().get());
      assertEquals(5, client.currentRevision().orElseThrow());
      assertTrue(client.boolVariation("release", CONTEXT, false));

      response.set(new Response(200, snapshot(4, false), "\"revision:4:stale\""));
      assertFalse(client.refreshAsync().get());
      assertEquals(5, client.currentRevision().orElseThrow());

      response.set(new Response(503, "unavailable", null));
      assertFalse(client.refreshAsync().get());
      assertTrue(client.boolVariation("release", CONTEXT, false));

      client.close();
      client.close();
      assertTrue(client.boolVariation("release", CONTEXT, false));
    }
  }

  @Test
  void networkInterruptionAndEdgeRestartConvergeWithoutLosingLastKnownGood() throws Exception {
    AtomicReference<Response> response =
        new AtomicReference<>(new Response(200, snapshot(10, true), "\"revision:10:valid\""));
    try (TestServer server =
            new TestServer(response, new AtomicReference<>(), new AtomicReference<>());
        LaunchForgeClient client = blockingClient(server.uri())) {
      server.setServing(false);
      assertFalse(client.refreshAsync().get(2, TimeUnit.SECONDS));
      assertEquals(10, client.currentRevision().orElseThrow());
      assertTrue(client.boolVariation("release", CONTEXT, false));

      response.set(new Response(200, snapshot(11, false), "\"revision:11:valid\""));
      server.setServing(true);
      assertTrue(client.refreshAsync().get(2, TimeUnit.SECONDS));
      assertEquals(11, client.currentRevision().orElseThrow());
      assertFalse(client.boolVariation("release", CONTEXT, true));
    }
  }

  @Test
  void concurrentReadersObserveOnlyCompleteOldOrNewRevisions() throws Exception {
    AtomicReference<Response> response =
        new AtomicReference<>(new Response(200, snapshot(1, true), "\"revision:1:valid\""));
    try (TestServer server =
            new TestServer(response, new AtomicReference<>(), new AtomicReference<>());
        LaunchForgeClient client = blockingClient(server.uri());
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      response.set(new Response(200, snapshot(2, false), "\"revision:2:valid\""));
      var refresh = client.refreshAsync();
      Set<String> observations = ConcurrentHashMap.newKeySet();
      var evaluations =
          java.util.stream.IntStream.range(0, 5_000)
              .mapToObj(
                  ignored ->
                      executor.submit(
                          () -> {
                            EvaluationDetail<Boolean> detail =
                                client.boolVariationDetail("release", CONTEXT, true);
                            observations.add(
                                detail.snapshotRevision().orElseThrow() + ":" + detail.value());
                          }))
              .toList();
      for (var evaluation : evaluations) {
        evaluation.get();
      }
      assertTrue(refresh.get());

      assertTrue(observations.stream().allMatch(Set.of("1:true", "2:false")::contains));
      assertEquals(2, client.currentRevision().orElseThrow());
      assertFalse(client.boolVariation("release", CONTEXT, true));
    }
  }

  @Test
  void blockingBootstrapFailsWithinItsConfiguredBound() throws Exception {
    AtomicReference<Response> response = new AtomicReference<>(new Response(503, "down", null));
    try (TestServer server =
        new TestServer(response, new AtomicReference<>(), new AtomicReference<>())) {
      assertThrows(
          BootstrapException.class,
          () ->
              LaunchForgeClient.builder()
                  .sdkKey("sdk_test_secret")
                  .baseUri(server.uri())
                  .requestTimeout(Duration.ofMillis(200))
                  .blockingBootstrap(Duration.ofSeconds(1))
                  .build());
    }
  }

  @Test
  void configuredPollWindowProducesBoundedJitter() {
    LaunchForgeClient client =
        LaunchForgeClient.builder()
            .sdkKey("sdk_test_jitter")
            .baseUri(URI.create("http://127.0.0.1:1"))
            .connectTimeout(Duration.ofMillis(50))
            .requestTimeout(Duration.ofMillis(50))
            .pollingInterval(Duration.ofSeconds(1), Duration.ofSeconds(2))
            .build();
    client.close();

    Set<Long> delays =
        java.util.stream.IntStream.range(0, 100)
            .mapToObj(ignored -> client.randomPollDelayMillis())
            .collect(java.util.stream.Collectors.toSet());

    assertTrue(delays.size() > 1);
    assertTrue(delays.stream().allMatch(delay -> delay >= 1_000 && delay <= 2_000));
  }

  @Test
  void revisionStreamTriggersConditionalFetchAndCleanShutdown() throws Exception {
    AtomicReference<Response> response =
        new AtomicReference<>(new Response(200, snapshot(1, true), "\"revision:1:valid\""));
    AtomicReference<String> lastEventId = new AtomicReference<>();
    AtomicInteger snapshotRequests = new AtomicInteger();
    AtomicInteger streamRequests = new AtomicInteger();
    AtomicReference<String> streamBody =
        new AtomicReference<>("event: revision\nid: 2\ndata: {\"revision\":2}\n\n");
    try (TestServer server =
        new TestServer(
            response,
            new AtomicReference<>(),
            new AtomicReference<>(),
            streamBody,
            lastEventId,
            snapshotRequests,
            streamRequests)) {
      LaunchForgeClient client = streamingClient(server.uri(), Duration.ofMinutes(5));
      await(() -> streamRequests.get() >= 1, Duration.ofSeconds(2));
      response.set(new Response(200, snapshot(2, false), "\"revision:2:valid\""));

      await(() -> client.currentRevision().orElse(0) == 2, Duration.ofSeconds(2));
      assertEquals("1", lastEventId.get());
      assertFalse(client.boolVariation("release", CONTEXT, true));
      assertTrue(snapshotRequests.get() >= 2);

      client.close();
      int requestsAtClose = streamRequests.get();
      TimeUnit.MILLISECONDS.sleep(100);
      assertEquals(requestsAtClose, streamRequests.get());
      assertFalse(client.refreshAsync().get());
      assertFalse(client.boolVariation("release", CONTEXT, true));
    }
  }

  @Test
  void pollingConvergesWhileRepeatedStreamDisconnectsRetainLastKnownGood() throws Exception {
    AtomicReference<Response> response =
        new AtomicReference<>(new Response(200, snapshot(3, true), "\"revision:3:valid\""));
    AtomicInteger streamRequests = new AtomicInteger();
    try (TestServer server =
            new TestServer(
                response,
                new AtomicReference<>(),
                new AtomicReference<>(),
                new AtomicReference<>(""),
                new AtomicReference<>(),
                new AtomicInteger(),
                streamRequests);
        LaunchForgeClient client = streamingClient(server.uri(), Duration.ofMillis(20))) {
      response.set(new Response(503, "down", null));
      await(() -> streamRequests.get() >= 2, Duration.ofSeconds(2));
      assertEquals(3, client.currentRevision().orElseThrow());
      assertTrue(client.boolVariation("release", CONTEXT, false));

      response.set(new Response(200, snapshot(4, false), "\"revision:4:valid\""));
      await(() -> client.currentRevision().orElse(0) == 4, Duration.ofSeconds(2));
      assertFalse(client.boolVariation("release", CONTEXT, true));
    }
  }

  @Test
  void reconnectBackoffIsExponentialBoundedAndJittered() {
    LaunchForgeClient client =
        LaunchForgeClient.builder()
            .sdkKey("sdk_test_stream_jitter")
            .baseUri(URI.create("http://127.0.0.1:1"))
            .connectTimeout(Duration.ofMillis(50))
            .requestTimeout(Duration.ofMillis(50))
            .pollingInterval(Duration.ofMinutes(5), Duration.ofMinutes(5))
            .streamReconnectBackoff(Duration.ofMillis(100), Duration.ofMillis(800))
            .build();
    client.close();

    for (int attempt = 1; attempt <= 8; attempt++) {
      long cap = Math.min(800, 100L << Math.min(attempt - 1, 3));
      long floor = Math.max(100, cap / 2);
      long delay = client.randomStreamReconnectDelayMillis(attempt);
      assertTrue(delay >= floor && delay <= cap);
    }
  }

  @Test
  void analyticsIsExplicitPrivacyBoundedAndCannotBreakEvaluation() throws Exception {
    AtomicReference<Response> response =
        new AtomicReference<>(new Response(200, snapshot(8, true), "\"revision:8:valid\""));
    try (TestServer server =
        new TestServer(response, new AtomicReference<>(), new AtomicReference<>())) {
      EvaluationContext sensitive =
          EvaluationContext.builder("private-subject")
              .attribute("email", "person@example.test")
              .build();
      try (LaunchForgeClient disabled = blockingClient(server.uri())) {
        assertTrue(disabled.boolVariation("release", sensitive, false));
        TimeUnit.MILLISECONDS.sleep(150);
        assertEquals(0, server.analyticsRequests());
      }

      server.setAnalyticsStatus(503);
      try (LaunchForgeClient enabled =
          LaunchForgeClient.builder()
              .sdkKey("sdk_test_secret")
              .baseUri(server.uri())
              .pollingInterval(Duration.ofMinutes(5), Duration.ofMinutes(5))
              .blockingBootstrap(Duration.ofSeconds(2))
              .analytics(new AnalyticsOptions(4, 1, Duration.ofMillis(100), Duration.ofMillis(500)))
              .build()) {
        assertTrue(enabled.boolVariation("release", sensitive, false));
        await(() -> server.analyticsRequests() == 1, Duration.ofSeconds(2));

        assertFalse(server.analyticsBody().contains("private-subject"));
        assertFalse(server.analyticsBody().contains("person@example.test"));
        assertFalse(server.analyticsBody().contains("attributes"));
        assertTrue(enabled.boolVariation("release", sensitive, false));
      }
    }
  }

  private static LaunchForgeClient blockingClient(URI uri) {
    return LaunchForgeClient.builder()
        .sdkKey("sdk_test_secret")
        .baseUri(uri)
        .connectTimeout(Duration.ofSeconds(1))
        .requestTimeout(Duration.ofSeconds(1))
        .pollingInterval(Duration.ofMinutes(5), Duration.ofMinutes(5))
        .blockingBootstrap(Duration.ofSeconds(2))
        .build();
  }

  private static LaunchForgeClient streamingClient(URI uri, Duration pollInterval) {
    return LaunchForgeClient.builder()
        .sdkKey("sdk_test_secret")
        .baseUri(uri)
        .connectTimeout(Duration.ofSeconds(1))
        .requestTimeout(Duration.ofSeconds(1))
        .pollingInterval(pollInterval, pollInterval)
        .streaming(true)
        .streamReconnectBackoff(Duration.ofMillis(20), Duration.ofMillis(80))
        .blockingBootstrap(Duration.ofSeconds(2))
        .build();
  }

  private static void await(BooleanSupplier condition, Duration timeout) throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() >= deadline) {
        throw new AssertionError("Condition was not met within " + timeout);
      }
      TimeUnit.MILLISECONDS.sleep(10);
    }
  }

  private static String snapshot(long revision, boolean value) {
    var root = SnapshotTestData.root(revision);
    root.withObject("flags").set("release", SnapshotTestData.booleanFlag(true, false, value));
    return SnapshotTestData.canonicalSnapshot(root);
  }

  private record Response(int status, String body, String etag) {}

  private static final class TestServer implements AutoCloseable {
    private final ServerSocket socket;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean serving = new AtomicBoolean(true);
    private final AtomicReference<Response> response;
    private final AtomicReference<String> authorization;
    private final AtomicReference<String> ifNoneMatch;
    private final AtomicReference<String> streamBody;
    private final AtomicReference<String> lastEventId;
    private final AtomicInteger snapshotRequests;
    private final AtomicInteger streamRequests;
    private final AtomicInteger analyticsRequests = new AtomicInteger();
    private final AtomicInteger analyticsStatus = new AtomicInteger(202);
    private final AtomicReference<String> analyticsBody = new AtomicReference<>("");

    TestServer(
        AtomicReference<Response> response,
        AtomicReference<String> authorization,
        AtomicReference<String> ifNoneMatch)
        throws IOException {
      this(
          response,
          authorization,
          ifNoneMatch,
          null,
          new AtomicReference<>(),
          new AtomicInteger(),
          new AtomicInteger());
    }

    TestServer(
        AtomicReference<Response> response,
        AtomicReference<String> authorization,
        AtomicReference<String> ifNoneMatch,
        AtomicReference<String> streamBody,
        AtomicReference<String> lastEventId,
        AtomicInteger snapshotRequests,
        AtomicInteger streamRequests)
        throws IOException {
      this.response = response;
      this.authorization = authorization;
      this.ifNoneMatch = ifNoneMatch;
      this.streamBody = streamBody;
      this.lastEventId = lastEventId;
      this.snapshotRequests = snapshotRequests;
      this.streamRequests = streamRequests;
      socket = new ServerSocket();
      socket.bind(new InetSocketAddress("127.0.0.1", 0));
      executor.submit(this::acceptRequests);
    }

    URI uri() {
      return URI.create("http://127.0.0.1:" + socket.getLocalPort());
    }

    @Override
    public void close() throws IOException {
      running.set(false);
      socket.close();
      executor.close();
    }

    void setServing(boolean value) {
      serving.set(value);
    }

    void setAnalyticsStatus(int value) {
      analyticsStatus.set(value);
    }

    int analyticsRequests() {
      return analyticsRequests.get();
    }

    String analyticsBody() {
      return analyticsBody.get();
    }

    private void acceptRequests() {
      while (running.get()) {
        try {
          Socket connection = socket.accept();
          executor.submit(() -> handle(connection));
        } catch (IOException exception) {
          if (running.get()) {
            throw new IllegalStateException("Test server accept failed", exception);
          }
        }
      }
    }

    private void handle(Socket connection) {
      try (connection;
          BufferedReader reader =
              new BufferedReader(
                  new InputStreamReader(
                      connection.getInputStream(), StandardCharsets.ISO_8859_1))) {
        String requestLine = reader.readLine();
        if (requestLine == null || !serving.get()) {
          return;
        }
        int contentLength = 0;
        for (String line = reader.readLine();
            line != null && !line.isEmpty();
            line = reader.readLine()) {
          int separator = line.indexOf(':');
          if (separator > 0) {
            String name = line.substring(0, separator);
            String value = line.substring(separator + 1).strip();
            if (name.equalsIgnoreCase("Authorization")) {
              authorization.set(value);
            } else if (name.equalsIgnoreCase("If-None-Match")) {
              ifNoneMatch.set(value);
            } else if (name.equalsIgnoreCase("Last-Event-ID")) {
              lastEventId.compareAndSet(null, value);
            } else if (name.equalsIgnoreCase("Content-Length")) {
              contentLength = Integer.parseInt(value);
            }
          }
        }
        char[] requestBody = new char[contentLength];
        int offset = 0;
        while (offset < requestBody.length) {
          int read = reader.read(requestBody, offset, requestBody.length - offset);
          if (read < 0) {
            break;
          }
          offset += read;
        }
        if (requestLine.startsWith("GET /sdk/v1/snapshot ")) {
          snapshotRequests.incrementAndGet();
          writeResponse(connection.getOutputStream(), response.get());
        } else if (requestLine.startsWith("GET /sdk/v1/stream ") && streamBody != null) {
          streamRequests.incrementAndGet();
          writeStream(connection.getOutputStream(), streamBody.get());
        } else if (requestLine.startsWith("POST /events/v1/evaluations/batch ")) {
          analyticsBody.set(new String(requestBody, 0, offset));
          analyticsRequests.incrementAndGet();
          writeResponse(
              connection.getOutputStream(), new Response(analyticsStatus.get(), "{}", null));
        }
      } catch (IOException exception) {
        if (running.get()) {
          throw new IllegalStateException("Test server response failed", exception);
        }
      }
    }

    private static void writeResponse(OutputStream output, Response response) throws IOException {
      byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
      String reason =
          response.status() == 200 ? "OK" : response.status() == 304 ? "Not Modified" : "Error";
      StringBuilder headers =
          new StringBuilder("HTTP/1.1 ")
              .append(response.status())
              .append(' ')
              .append(reason)
              .append("\r\nConnection: close\r\n");
      if (response.etag() != null) {
        headers.append("ETag: ").append(response.etag()).append("\r\n");
      }
      if (response.status() == 304) {
        headers.append("Content-Length: 0\r\n\r\n");
      } else {
        headers
            .append("Content-Type: application/json\r\nContent-Length: ")
            .append(body.length)
            .append("\r\n\r\n");
      }
      output.write(headers.toString().getBytes(StandardCharsets.ISO_8859_1));
      if (response.status() != 304) {
        output.write(body);
      }
      output.flush();
    }

    private static void writeStream(OutputStream output, String bodyValue) throws IOException {
      byte[] body = bodyValue.getBytes(StandardCharsets.UTF_8);
      String headers =
          "HTTP/1.1 200 OK\r\n"
              + "Content-Type: text/event-stream\r\n"
              + "Connection: close\r\n"
              + "Content-Length: "
              + body.length
              + "\r\n\r\n";
      output.write(headers.getBytes(StandardCharsets.ISO_8859_1));
      output.write(body);
      output.flush();
    }
  }
}
