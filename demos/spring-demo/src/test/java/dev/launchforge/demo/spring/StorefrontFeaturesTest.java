package dev.launchforge.demo.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.launchforge.sdk.LaunchForgeClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.erdtman.jcs.JsonCanonicalizer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class StorefrontFeaturesTest {
  @Test
  @SuppressWarnings("try")
  void twoTypedFlagsKeepWorkingAfterSnapshotServiceStops() throws Exception {
    try (OneSnapshotServer server = new OneSnapshotServer(snapshot());
        LaunchForgeClient client =
            LaunchForgeClient.builder()
                .baseUri(server.uri())
                .sdkKey("sdk_demo_test")
                .connectTimeout(Duration.ofMillis(200))
                .requestTimeout(Duration.ofMillis(500))
                .pollingInterval(Duration.ofMinutes(5), Duration.ofMinutes(5))
                .blockingBootstrap(Duration.ofSeconds(2))
                .build()) {
      server.close();
      StorefrontFeatures features = new StorefrontFeatures(client);

      StorefrontFeatures.FeatureResponse response =
          features.evaluate("customer-123", "CA", "pro", "customer-123");

      assertTrue(response.newCheckout());
      assertEquals("hybrid-v2", response.searchRanking());
      assertEquals(7, response.snapshotRevision());
      assertFalse(client.refreshAsync().get());
      assertEquals(response, features.evaluate("customer-123", "CA", "pro", "customer-123"));
    }
  }

  private static String snapshot() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode root = mapper.createObjectNode();
    root.put("schemaVersion", 1);
    root.put("algorithmVersion", 1);
    root.put("projectKey", "fictional-storefront");
    root.put("environmentKey", "demo");
    root.put("revision", 7);
    root.put("generatedAt", "2026-08-11T12:00:00Z");
    ObjectNode flags = root.putObject("flags");
    flags.set("new-checkout", flag(mapper, "boolean", false, true));
    flags.set("search-ranking", flag(mapper, "string", "lexical-v1", "hybrid-v2"));
    String projection = new JsonCanonicalizer(mapper.writeValueAsString(root)).getEncodedString();
    root.put(
        "checksum",
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(projection.getBytes(StandardCharsets.UTF_8))));
    return new JsonCanonicalizer(mapper.writeValueAsString(root)).getEncodedString();
  }

  private static ObjectNode flag(ObjectMapper mapper, String type, Object off, Object on) {
    ObjectNode flag = mapper.createObjectNode();
    flag.put("type", type);
    flag.put("enabled", true);
    flag.put("clientVisible", true);
    ArrayNode variations = flag.putArray("variations");
    variations.addObject().put("id", "off").set("value", mapper.valueToTree(off));
    variations.addObject().put("id", "on").set("value", mapper.valueToTree(on));
    flag.put("offVariation", "off");
    flag.put("defaultVariation", "on");
    flag.putArray("rules");
    return flag;
  }

  private static final class OneSnapshotServer implements AutoCloseable {
    private final ServerSocket serverSocket = new ServerSocket();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final String snapshot;

    OneSnapshotServer(String snapshot) throws IOException {
      this.snapshot = snapshot;
      serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
      executor.submit(this::serve);
    }

    URI uri() {
      return URI.create("http://127.0.0.1:" + serverSocket.getLocalPort());
    }

    @Override
    public void close() throws IOException {
      serverSocket.close();
      executor.close();
    }

    private void serve() {
      try (Socket socket = serverSocket.accept()) {
        while (socket.getInputStream().read() != -1) {
          if (socket.getInputStream().available() == 0) {
            break;
          }
        }
        byte[] body = snapshot.getBytes(StandardCharsets.UTF_8);
        String headers =
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                + body.length
                + "\r\nConnection: close\r\n\r\n";
        socket.getOutputStream().write(headers.getBytes(StandardCharsets.ISO_8859_1));
        socket.getOutputStream().write(body);
        socket.getOutputStream().flush();
      } catch (IOException exception) {
        if (!serverSocket.isClosed()) {
          throw new IllegalStateException("Demo test server failed", exception);
        }
      }
    }
  }
}
