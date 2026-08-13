package dev.launchforge.eventworker.analytics;

import dev.launchforge.eventworker.configuration.AnalyticsWorkerProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "launchforge.analytics.worker.enabled", havingValue = "true")
public final class ClickHouseAnalyticsStore implements AnalyticsStore, AutoCloseable {
  private static final String INSERT =
      "INSERT INTO launchforge.evaluation_events FORMAT JSONEachRow\n";
  private final AnalyticsWorkerProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final URI endpoint;

  public ClickHouseAnalyticsStore(AnalyticsWorkerProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    String base = properties.clickHouseUrl().toString().replaceAll("/+$", "");
    endpoint = URI.create(base + "/");
  }

  @Override
  public void insert(List<AnalyticsEventRow> rows) {
    if (rows.isEmpty()) {
      return;
    }
    try {
      StringBuilder body = new StringBuilder(INSERT);
      for (AnalyticsEventRow row : rows) {
        body.append(objectMapper.writeValueAsString(asDocument(row))).append('\n');
      }
      HttpRequest request =
          HttpRequest.newBuilder(endpoint)
              .timeout(properties.requestTimeout())
              .header("Content-Type", "text/plain; charset=utf-8")
              .header("X-ClickHouse-User", properties.clickHouseUser())
              .header("X-ClickHouse-Key", properties.clickHousePassword())
              .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "ClickHouse insert failed with status " + response.statusCode());
      }
    } catch (JacksonException exception) {
      throw new IllegalStateException("Analytics row encoding failed", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("ClickHouse insert transport failed", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("ClickHouse insert was interrupted", exception);
    }
  }

  @Override
  public void close() {
    httpClient.shutdownNow();
  }

  private static Map<String, Object> asDocument(AnalyticsEventRow row) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("event_id", row.eventId());
    value.put("occurred_at", row.occurredAt().toString());
    value.put("received_at", row.receivedAt().toString());
    value.put("organization_id", row.organizationId());
    value.put("project_id", row.projectId());
    value.put("environment_id", row.environmentId());
    value.put("project_key", row.projectKey());
    value.put("environment_key", row.environmentKey());
    value.put("flag_key", row.flagKey());
    value.put("variation_id", row.variationId() == null ? "" : row.variationId());
    value.put("reason", row.reason());
    value.put("revision", row.revision());
    value.put("source", row.source());
    return value;
  }
}
