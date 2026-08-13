package dev.launchforge.controlapi.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.launchforge.application.controlplane.ControlPlaneService.AnalyticsScope;
import dev.launchforge.controlapi.analytics.AnalyticsQueryGateway.BucketSize;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AnalyticsQueryGatewayTest {
  @Test
  void sendsTypedParametersAndDecodesOnlyOperationalCounts() throws Exception {
    AtomicReference<String> request = new AtomicReference<>();
    try (TestServer server =
            new TestServer(
                200,
                """
        {"bucketStart":"2026-08-13T12:00:00Z","flagKey":"checkout","variationId":"on","evaluations":7}
        """,
                request);
        AnalyticsQueryGateway gateway = gateway(server.uri())) {
      var result =
          gateway.query(
              scope(),
              Instant.parse("2026-08-13T00:00:00Z"),
              Instant.parse("2026-08-14T00:00:00Z"),
              "checkout",
              "on",
              BucketSize.HOUR,
              50);

      assertEquals(7, result.getFirst().evaluations());
      assertTrue(request.get().contains("{environmentId:UUID}"));
      assertTrue(request.get().contains("uniqExact(event_id)"));
      assertTrue(request.get().contains("LIMIT {limit:UInt32}"));
    }
  }

  @Test
  void clickHouseOutageReturnsIsolatedUnavailableError() throws Exception {
    try (TestServer server = new TestServer(503, "down", new AtomicReference<>());
        AnalyticsQueryGateway gateway = gateway(server.uri())) {
      assertThrows(
          AnalyticsUnavailableException.class,
          () ->
              gateway.query(
                  scope(),
                  Instant.parse("2026-08-13T00:00:00Z"),
                  Instant.parse("2026-08-14T00:00:00Z"),
                  null,
                  null,
                  BucketSize.DAY,
                  50));
    }
  }

  private static AnalyticsQueryGateway gateway(URI uri) {
    AnalyticsQueryProperties properties =
        new AnalyticsQueryProperties(
            true,
            uri,
            "launchforge",
            "test",
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            2,
            Duration.ofDays(31),
            1000);
    return new AnalyticsQueryGateway(
        properties, new AnalyticsQueryMetrics(new SimpleMeterRegistry()), new ObjectMapper());
  }

  private static AnalyticsScope scope() {
    return new AnalyticsScope(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "storefront", "production");
  }

  private static final class TestServer implements AutoCloseable {
    private final ServerSocket server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final int status;
    private final String response;
    private final AtomicReference<String> request;

    TestServer(int status, String response, AtomicReference<String> request) throws IOException {
      this.status = status;
      this.response = response;
      this.request = request;
      server = new ServerSocket();
      server.bind(new InetSocketAddress("127.0.0.1", 0));
      executor.submit(this::serveOne);
    }

    URI uri() {
      return URI.create("http://127.0.0.1:" + server.getLocalPort());
    }

    @Override
    public void close() throws IOException {
      server.close();
      executor.close();
    }

    private void serveOne() {
      try (Socket connection = server.accept();
          BufferedReader reader =
              new BufferedReader(
                  new InputStreamReader(
                      connection.getInputStream(), StandardCharsets.ISO_8859_1))) {
        reader.readLine();
        int contentLength = 0;
        for (String line = reader.readLine();
            line != null && !line.isEmpty();
            line = reader.readLine()) {
          if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
            contentLength = Integer.parseInt(line.substring(15).strip());
          }
        }
        char[] body = new char[contentLength];
        int offset = 0;
        while (offset < contentLength) {
          int read = reader.read(body, offset, contentLength - offset);
          if (read < 0) {
            break;
          }
          offset += read;
        }
        request.set(new String(body, 0, offset));
        write(connection.getOutputStream());
      } catch (IOException exception) {
        if (!server.isClosed()) {
          throw new IllegalStateException(exception);
        }
      }
    }

    private void write(OutputStream output) throws IOException {
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      String headers =
          "HTTP/1.1 "
              + status
              + " Test\r\nConnection: close\r\nContent-Type: application/json\r\nContent-Length: "
              + bytes.length
              + "\r\n\r\n";
      output.write(headers.getBytes(StandardCharsets.ISO_8859_1));
      output.write(bytes);
      output.flush();
    }
  }
}
