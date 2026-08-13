package dev.launchforge.controlapi.analytics;

import dev.launchforge.application.controlplane.ControlPlaneService.AnalyticsScope;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
final class AnalyticsQueryGateway implements AutoCloseable {
  private final AnalyticsQueryProperties properties;
  private final AnalyticsQueryMetrics metrics;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final Semaphore concurrent;

  AnalyticsQueryGateway(
      AnalyticsQueryProperties properties,
      AnalyticsQueryMetrics metrics,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.metrics = metrics;
    this.objectMapper = objectMapper;
    httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    concurrent = new Semaphore(properties.maximumConcurrentQueries());
  }

  List<AnalyticsBucket> query(
      AnalyticsScope scope,
      Instant from,
      Instant to,
      String flagKey,
      String variationId,
      BucketSize bucket,
      int limit) {
    if (!properties.enabled()) {
      throw new AnalyticsUnavailableException("Optional analytics is disabled");
    }
    if (!concurrent.tryAcquire()) {
      metrics.shed();
      throw new AnalyticsQueryCapacityException();
    }
    io.micrometer.core.instrument.Timer.Sample sample = metrics.start();
    try {
      URI endpoint = endpoint(scope, from, to, flagKey, variationId, limit);
      HttpRequest request =
          HttpRequest.newBuilder(endpoint)
              .timeout(properties.requestTimeout())
              .header("Content-Type", "text/plain; charset=utf-8")
              .header("X-ClickHouse-User", properties.clickHouseUser())
              .header("X-ClickHouse-Key", properties.clickHousePassword())
              .POST(HttpRequest.BodyPublishers.ofString(sql(bucket), StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new AnalyticsUnavailableException(
            "Analytics query failed with status " + response.statusCode());
      }
      List<AnalyticsBucket> result = decode(response.body());
      metrics.success(sample);
      return result;
    } catch (IOException exception) {
      metrics.failure(sample);
      throw new AnalyticsUnavailableException("Analytics query transport failed", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      metrics.failure(sample);
      throw new AnalyticsUnavailableException("Analytics query was interrupted", exception);
    } catch (RuntimeException exception) {
      metrics.failure(sample);
      throw exception;
    } finally {
      concurrent.release();
    }
  }

  @Override
  public void close() {
    httpClient.shutdownNow();
  }

  private URI endpoint(
      AnalyticsScope scope,
      Instant from,
      Instant to,
      String flagKey,
      String variationId,
      int limit) {
    String base = properties.clickHouseUrl().toString().replaceAll("/+$", "");
    String query =
        "?max_execution_time=3&max_result_rows="
            + properties.maximumRows()
            + "&result_overflow_mode=break"
            + parameter("organizationId", scope.organizationId().toString())
            + parameter("projectId", scope.projectId().toString())
            + parameter("environmentId", scope.environmentId().toString())
            + parameter("from", from.toString())
            + parameter("to", to.toString())
            + parameter("flagKey", flagKey == null ? "" : flagKey)
            + parameter("variationId", variationId == null ? "" : variationId)
            + parameter("limit", Integer.toString(limit));
    return URI.create(base + "/" + query);
  }

  private static String parameter(String name, String value) {
    return "&param_" + name + '=' + URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String sql(BucketSize bucket) {
    String interval = bucket == BucketSize.HOUR ? "HOUR" : "DAY";
    return """
        SELECT formatDateTime(toStartOfInterval(occurred_at, INTERVAL 1 %s),
                              '%%Y-%%m-%%dT%%H:%%i:%%SZ', 'UTC') AS bucketStart,
               flag_key AS flagKey,
               variation_id AS variationId,
               uniqExact(event_id) AS evaluations
          FROM launchforge.evaluation_events
         WHERE organization_id = {organizationId:UUID}
           AND project_id = {projectId:UUID}
           AND environment_id = {environmentId:UUID}
           AND occurred_at >= parseDateTime64BestEffort({from:String}, 3)
           AND occurred_at < parseDateTime64BestEffort({to:String}, 3)
           AND ({flagKey:String} = '' OR flag_key = {flagKey:String})
           AND ({variationId:String} = '' OR variation_id = {variationId:String})
         GROUP BY bucketStart, flagKey, variationId
         ORDER BY bucketStart ASC, flagKey ASC, variationId ASC
         LIMIT {limit:UInt32}
         FORMAT JSONEachRow
        """
        .formatted(interval);
  }

  private List<AnalyticsBucket> decode(String body) {
    List<AnalyticsBucket> values = new ArrayList<>();
    try {
      for (String line : body.lines().filter(value -> !value.isBlank()).toList()) {
        JsonNode row = objectMapper.readTree(line);
        values.add(
            new AnalyticsBucket(
                Instant.parse(row.path("bucketStart").stringValue()),
                row.path("flagKey").stringValue(),
                emptyToNull(row.path("variationId").stringValue()),
                row.path("evaluations").longValue()));
      }
      return List.copyOf(values);
    } catch (RuntimeException exception) {
      throw new AnalyticsUnavailableException(
          "Analytics query returned an invalid response", exception);
    }
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  enum BucketSize {
    HOUR,
    DAY
  }

  record AnalyticsBucket(
      Instant bucketStart, String flagKey, String variationId, long evaluations) {}
}
