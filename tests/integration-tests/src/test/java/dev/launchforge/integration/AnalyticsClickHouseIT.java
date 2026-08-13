package dev.launchforge.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.launchforge.eventworker.analytics.AnalyticsEventRow;
import dev.launchforge.eventworker.analytics.ClickHouseAnalyticsStore;
import dev.launchforge.eventworker.configuration.AnalyticsWorkerProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@Testcontainers(disabledWithoutDocker = true)
class AnalyticsClickHouseIT {
  private static final String USER = "launchforge";
  private static final String PASSWORD = "analytics-test-password";

  @Container
  private static final GenericContainer<?> CLICKHOUSE =
      new GenericContainer<>(
              DockerImageName.parse(
                  "clickhouse:26.7.1.1315@sha256:16537a9270ad63acbbee437ebbb826ea62b49690e863ae33e2fc5c16b7d9466c"))
          .withEnv("CLICKHOUSE_DB", "launchforge")
          .withEnv("CLICKHOUSE_USER", USER)
          .withEnv("CLICKHOUSE_PASSWORD", PASSWORD)
          .withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1")
          .withExposedPorts(8123)
          .waitingFor(
              Wait.forHttp("/ping")
                  .forPort(8123)
                  .withHeader("X-ClickHouse-User", USER)
                  .withHeader("X-ClickHouse-Key", PASSWORD)
                  .forResponsePredicate(body -> "Ok.".equals(body.strip()))
                  .withStartupTimeout(Duration.ofMinutes(2)));

  @Test
  void storesPrivacyBoundedRowsWithRetentionAndDuplicateTolerantAggregates() throws Exception {
    URI endpoint = endpoint();
    executeSchema(endpoint, Files.readString(schemaPath(), StandardCharsets.UTF_8));
    UUID eventId = UUID.randomUUID();
    AnalyticsEventRow row =
        new AnalyticsEventRow(
            eventId,
            Instant.parse("2026-08-13T12:00:00Z"),
            Instant.parse("2026-08-13T12:00:01Z"),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "storefront",
            "production",
            "checkout",
            "on",
            "RULE_MATCH",
            4,
            "SERVER");
    AnalyticsWorkerProperties properties =
        new AnalyticsWorkerProperties(
            true,
            "launchforge.analytics.evaluations.v1",
            "analytics-it",
            1,
            1,
            100,
            10,
            Duration.ofSeconds(1),
            endpoint,
            USER,
            PASSWORD,
            Duration.ofSeconds(2),
            Duration.ofSeconds(5));
    try (ClickHouseAnalyticsStore store =
        new ClickHouseAnalyticsStore(properties, new ObjectMapper())) {
      store.insert(List.of(row, row));
    }

    String counts =
        execute(
                endpoint,
                "SELECT count(), uniqExact(event_id) FROM launchforge.evaluation_events FORMAT TSV")
            .strip();
    assertEquals("2\t1", counts);
    String definition =
        execute(
            endpoint,
            "SELECT create_table_query FROM system.tables WHERE database='launchforge' AND name='evaluation_events' FORMAT TabSeparatedRaw");
    assertTrue(definition.contains("TTL occurred_at + toIntervalDay(90)"));
    String columns = execute(endpoint, "DESCRIBE TABLE launchforge.evaluation_events FORMAT TSV");
    assertFalse(columns.contains("context"));
    assertFalse(columns.contains("subject"));
  }

  private static void executeSchema(URI endpoint, String schema)
      throws IOException, InterruptedException {
    for (String statement : schema.split(";")) {
      if (!statement.isBlank()) {
        execute(endpoint, statement);
      }
    }
  }

  private static String execute(URI endpoint, String sql) throws IOException, InterruptedException {
    try (HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
      HttpRequest request =
          HttpRequest.newBuilder(endpoint)
              .timeout(Duration.ofSeconds(10))
              .header("X-ClickHouse-User", USER)
              .header("X-ClickHouse-Key", PASSWORD)
              .POST(HttpRequest.BodyPublishers.ofString(sql, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("ClickHouse test request failed: " + response.body());
      }
      return response.body();
    }
  }

  private static URI endpoint() {
    return URI.create("http://127.0.0.1:" + CLICKHOUSE.getMappedPort(8123));
  }

  private static Path schemaPath() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      Path candidate = current.resolve("deploy/local/clickhouse/init/001_analytics.sql");
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("ClickHouse analytics schema was not found");
  }
}
