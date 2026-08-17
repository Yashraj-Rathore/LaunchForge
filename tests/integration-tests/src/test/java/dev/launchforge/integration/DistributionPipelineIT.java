package dev.launchforge.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.launchforge.configedge.LaunchForgeConfigEdgeApplication;
import dev.launchforge.configedge.persistence.EdgeRepository;
import dev.launchforge.configedge.security.EdgeRateLimiter;
import dev.launchforge.configedge.stream.ConnectionLimitExceededException;
import dev.launchforge.configedge.stream.StreamConnectionLimiter;
import dev.launchforge.contracts.events.ConfigRevisionPublishedEvent;
import dev.launchforge.contracts.sdk.ServerSdkKeyCredential;
import dev.launchforge.eventworker.LaunchForgeEventWorkerApplication;
import dev.launchforge.eventworker.outbox.JdbcOutboxRepository;
import dev.launchforge.eventworker.projection.ProjectionReconciler;
import dev.launchforge.eventworker.projection.RedisSnapshotMaterializer;
import dev.launchforge.sdk.EvaluationContext;
import dev.launchforge.sdk.LaunchForgeClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import org.erdtman.jcs.JsonCanonicalizer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** End-to-end M7 durability, convergence, and recovery evidence. */
@Testcontainers
class DistributionPipelineIT {
  private static final UUID ORGANIZATION_ID =
      UUID.fromString("71000000-0000-0000-0000-000000000001");
  private static final UUID PROJECT_ID = UUID.fromString("72000000-0000-0000-0000-000000000001");
  private static final UUID ENVIRONMENT_ID =
      UUID.fromString("73000000-0000-0000-0000-000000000001");
  private static final UUID SDK_KEY_ID = UUID.fromString("74000000-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");
  private static final String PEPPER = "distribution-test-pepper-with-more-than-thirty-two-bytes";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.4-bookworm")
          .withDatabaseName("launchforge_distribution_test")
          .withUsername("launchforge_distribution_test")
          .withPassword("integration-test-only");

  @Container
  private static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

  @Container
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:8.2.8-bookworm"))
          .withExposedPorts(6379)
          .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));

  private static JdbcTemplate jdbc;
  private static DataSource dataSource;
  private static ServerSdkKeyCredential.Generated credential;

  private ConfigurableApplicationContext worker;
  private ConfigurableApplicationContext edgeOne;
  private ConfigurableApplicationContext edgeTwo;

  @BeforeAll
  static void migrateAndSeed() {
    DriverManagerDataSource configured =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    dataSource = configured;
    Flyway.configure().dataSource(configured).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(configured);
    seedTenant();
  }

  @AfterEach
  void closeApplications() {
    close(edgeOne);
    close(edgeTwo);
    close(worker);
  }

  @Test
  @SuppressWarnings("unchecked")
  void durableDistributionConvergesAcrossFailuresAndMultipleEdges() throws Exception {
    proveLeaseRecovery();
    worker = startWorker();
    provePermanentOutboxFailure();

    Revision revisionOne = publishRevision(1, true);
    await(() -> outboxStatus(revisionOne.eventId()).equals("PUBLISHED"), Duration.ofSeconds(20));
    await(() -> redisRevision() == 1, Duration.ofSeconds(20));

    KafkaTemplate<String, String> kafkaTemplate = worker.getBean(KafkaTemplate.class);
    kafkaTemplate
        .send(
            ConfigRevisionPublishedEvent.EVENT_TYPE,
            ENVIRONMENT_ID.toString(),
            revisionOne.eventJson())
        .get(10, TimeUnit.SECONDS);
    TimeUnit.MILLISECONDS.sleep(250);
    assertEquals(1, redisRevision());

    edgeOne = startEdge();
    edgeTwo = startEdge();
    assertEquals(1, currentRevision(edgeOne));
    assertEquals(1, currentRevision(edgeTwo));
    proveDistributedAbuseControls(edgeOne, edgeTwo);
    proveSdkLastKnownGood(edgeOne);
    edgeOne = null;

    KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
    Revision revisionTwo;
    try {
      revisionTwo = publishRevision(2, false);
      await(() -> outboxAttempts(revisionTwo.eventId()) > 0, Duration.ofSeconds(10));
      assertFalse(outboxStatus(revisionTwo.eventId()).equals("PUBLISHED"));
      assertEquals(1, redisRevision());
    } finally {
      KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
    }
    await(() -> outboxStatus(revisionTwo.eventId()).equals("PUBLISHED"), Duration.ofSeconds(30));
    await(() -> redisRevision() == 2, Duration.ofSeconds(30));
    await(() -> currentRevision(edgeTwo) == 2, Duration.ofSeconds(10));

    edgeOne = startEdge();
    assertEquals(2, currentRevision(edgeOne));

    KafkaListenerEndpointRegistry listeners = worker.getBean(KafkaListenerEndpointRegistry.class);
    listeners.stop();
    Revision revisionThree = publishRevision(3, true);
    await(() -> outboxStatus(revisionThree.eventId()).equals("PUBLISHED"), Duration.ofSeconds(20));
    assertEquals(2, redisRevision());
    listeners.start();
    await(() -> redisRevision() == 3, Duration.ofSeconds(30));

    REDIS.execInContainer("redis-cli", "FLUSHALL");
    worker.getBean(ProjectionReconciler.class).reconcile();
    await(() -> redisRevision() == 3, Duration.ofSeconds(10));

    REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
    try {
      assertEquals(3, currentSnapshotRevision(edgeOne));
      assertEquals(3, currentSnapshotRevision(edgeTwo));
    } finally {
      REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
    }
  }

  private static void proveLeaseRecovery() {
    UUID eventId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO outbox_events
          (id, organization_id, aggregate_type, aggregate_id, aggregate_revision,
           event_type, schema_version, payload, status, available_at, created_at)
        VALUES (?, ?, 'ENVIRONMENT', ?, 99, ?, 1, '{}'::jsonb, 'PENDING', ?, ?)
        """,
        eventId,
        ORGANIZATION_ID,
        ENVIRONMENT_ID,
        ConfigRevisionPublishedEvent.EVENT_TYPE,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
    JdbcOutboxRepository repository = new JdbcOutboxRepository(jdbc);
    assertEquals(1, repository.lease("worker-a", 1, Duration.ofMinutes(1)).size());
    assertTrue(repository.lease("worker-b", 1, Duration.ofMinutes(1)).isEmpty());
    jdbc.update(
        "UPDATE outbox_events SET lease_until = clock_timestamp() - INTERVAL '1 second' WHERE id = ?",
        eventId);
    assertEquals(1, repository.lease("worker-b", 1, Duration.ofMinutes(1)).size());
    jdbc.update("DELETE FROM outbox_events WHERE id = ?", eventId);
  }

  private static void provePermanentOutboxFailure() throws Exception {
    UUID eventId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO outbox_events
          (id, organization_id, aggregate_type, aggregate_id, aggregate_revision,
           event_type, schema_version, payload, status, available_at, created_at)
        VALUES (?, ?, 'ENVIRONMENT', ?, 100, ?, 1, '{}'::jsonb, 'PENDING', ?, ?)
        """,
        eventId,
        ORGANIZATION_ID,
        ENVIRONMENT_ID,
        ConfigRevisionPublishedEvent.EVENT_TYPE,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
    await(() -> outboxStatus(eventId).equals("FAILED"), Duration.ofSeconds(10));
    assertEquals(
        "OUTBOX_EVENT_INVALID",
        jdbc.queryForObject(
            "SELECT last_error_code FROM outbox_events WHERE id = ?", String.class, eventId));
    jdbc.update("DELETE FROM outbox_events WHERE id = ?", eventId);
  }

  private static ConfigurableApplicationContext startWorker() {
    return new SpringApplicationBuilder(LaunchForgeEventWorkerApplication.class)
        .web(WebApplicationType.NONE)
        .run(
            arguments(
                "spring.flyway.enabled=false",
                "spring.autoconfigure.exclude="
                    + "org.springframework.boot.security.oauth2.client.autoconfigure."
                    + "OAuth2ClientAutoConfiguration,"
                    + "org.springframework.boot.security.oauth2.client.autoconfigure.reactive."
                    + "ReactiveOAuth2ClientAutoConfiguration,"
                    + "org.springframework.boot.security.oauth2.client.autoconfigure.reactive."
                    + "ReactiveOAuth2ClientWebSecurityAutoConfiguration",
                "spring.task.scheduling.pool.size=4",
                "launchforge.distribution.outbox-poll-interval=100ms",
                "launchforge.distribution.outbox-lease=2s",
                "launchforge.distribution.publish-timeout=500ms",
                "launchforge.distribution.retry-initial-backoff=100ms",
                "launchforge.distribution.retry-maximum-backoff=1s",
                "launchforge.distribution.reconciliation-interval=1h"));
  }

  private static ConfigurableApplicationContext startEdge() {
    return new SpringApplicationBuilder(LaunchForgeConfigEdgeApplication.class)
        .web(WebApplicationType.REACTIVE)
        .run(
            arguments(
                "server.port=0",
                "spring.flyway.enabled=false",
                "spring.autoconfigure.exclude="
                    + "org.springframework.boot.security.oauth2.client.autoconfigure.reactive."
                    + "ReactiveOAuth2ClientAutoConfiguration,"
                    + "org.springframework.boot.security.oauth2.client.autoconfigure.reactive."
                    + "ReactiveOAuth2ClientWebSecurityAutoConfiguration,"
                    + "org.springframework.boot.security.autoconfigure.web.reactive."
                    + "ReactiveWebSecurityAutoConfiguration,"
                    + "org.springframework.boot.security.autoconfigure.actuate.web.reactive."
                    + "ReactiveManagementWebSecurityAutoConfiguration",
                "launchforge.sdk-keys.peppers.v1=" + PEPPER,
                "launchforge.config-edge.revision-poll-interval=100ms",
                "launchforge.config-edge.heartbeat-interval=1s"));
  }

  private static String[] arguments(String... applicationArguments) {
    java.util.ArrayList<String> arguments = new java.util.ArrayList<>();
    arguments.add("--spring.datasource.url=" + POSTGRES.getJdbcUrl());
    arguments.add("--spring.datasource.username=" + POSTGRES.getUsername());
    arguments.add("--spring.datasource.password=" + POSTGRES.getPassword());
    arguments.add("--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers());
    arguments.add("--spring.data.redis.host=" + REDIS.getHost());
    arguments.add("--spring.data.redis.port=" + REDIS.getMappedPort(6379));
    arguments.add("--spring.data.redis.connect-timeout=500ms");
    arguments.add("--spring.data.redis.timeout=500ms");
    for (String argument : applicationArguments) {
      arguments.add("--" + argument);
    }
    return arguments.toArray(String[]::new);
  }

  private static Revision publishRevision(long revision, boolean enabled) throws Exception {
    Snapshot snapshot = snapshot(revision, enabled);
    UUID eventId = UUID.randomUUID();
    String eventJson = eventJson(eventId, revision, snapshot.checksum());
    TransactionTemplate transaction =
        new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    transaction.executeWithoutResult(
        status -> {
          jdbc.update(
              """
              INSERT INTO environment_revisions
                (organization_id, project_id, environment_id, revision, schema_version,
                 snapshot_json, canonical_snapshot, checksum_sha256,
                 actor_issuer, actor_subject, created_at)
              VALUES (?, ?, ?, ?, 1, CAST(? AS jsonb), ?, ?, 'test', 'distribution-test', ?)
              """,
              ORGANIZATION_ID,
              PROJECT_ID,
              ENVIRONMENT_ID,
              revision,
              snapshot.canonical(),
              snapshot.canonical(),
              snapshot.checksum(),
              Timestamp.from(NOW.plusSeconds(revision)));
          jdbc.update(
              "UPDATE environments SET current_revision = ?, version = version + 1, updated_at = ? WHERE id = ?",
              revision,
              Timestamp.from(NOW.plusSeconds(revision)),
              ENVIRONMENT_ID);
          jdbc.update(
              """
              INSERT INTO outbox_events
                (id, organization_id, aggregate_type, aggregate_id, aggregate_revision,
                 event_type, schema_version, payload, status, available_at, created_at)
              VALUES (?, ?, 'ENVIRONMENT', ?, ?, ?, 1, CAST(? AS jsonb), 'PENDING', ?, ?)
              """,
              eventId,
              ORGANIZATION_ID,
              ENVIRONMENT_ID,
              revision,
              ConfigRevisionPublishedEvent.EVENT_TYPE,
              eventJson,
              Timestamp.from(NOW.plusSeconds(revision)),
              Timestamp.from(NOW.plusSeconds(revision)));
        });
    return new Revision(eventId, eventJson);
  }

  private static void seedTenant() {
    jdbc.update(
        "INSERT INTO organizations (id, slug, name, status, version, created_at, updated_at) VALUES (?, 'distribution', 'Distribution', 'ACTIVE', 0, ?, ?)",
        ORGANIZATION_ID,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
    jdbc.update(
        "INSERT INTO projects (id, organization_id, project_key, name, status, version, created_at, updated_at) VALUES (?, ?, 'storefront', 'Storefront', 'ACTIVE', 0, ?, ?)",
        PROJECT_ID,
        ORGANIZATION_ID,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
    jdbc.update(
        """
        INSERT INTO environments
          (id, organization_id, project_id, environment_key, name, kind, status,
           current_revision, version, created_at, updated_at)
        VALUES (?, ?, ?, 'production', 'Production', 'PRODUCTION', 'ACTIVE', 0, 0, ?, ?)
        """,
        ENVIRONMENT_ID,
        ORGANIZATION_ID,
        PROJECT_ID,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
    credential =
        ServerSdkKeyCredential.generate(
            new SecureRandom(), "v1", PEPPER.getBytes(StandardCharsets.UTF_8));
    jdbc.update(
        """
        INSERT INTO sdk_keys
          (id, organization_id, project_id, environment_id, key_type, name, lookup_id,
           secret_verifier, pepper_version, fingerprint, status, created_at,
           created_by_issuer, created_by_subject)
        VALUES (?, ?, ?, ?, 'SERVER', 'Distribution test', ?, ?, 'v1', ?, 'ACTIVE', ?,
                'test', 'distribution-test')
        """,
        SDK_KEY_ID,
        ORGANIZATION_ID,
        PROJECT_ID,
        ENVIRONMENT_ID,
        credential.lookupId(),
        credential.verifier(),
        credential.fingerprint(),
        Timestamp.from(NOW));
  }

  private static Snapshot snapshot(long revision, boolean enabled) throws Exception {
    Map<String, Object> flag = new LinkedHashMap<>();
    flag.put("type", "boolean");
    flag.put("enabled", enabled);
    flag.put("clientVisible", false);
    flag.put(
        "variations",
        List.of(Map.of("id", "off", "value", false), Map.of("id", "on", "value", true)));
    flag.put("offVariation", "off");
    flag.put("defaultVariation", "on");
    flag.put("rules", List.of());
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schemaVersion", 1);
    document.put("algorithmVersion", 1);
    document.put("projectKey", "storefront");
    document.put("environmentKey", "production");
    document.put("revision", revision);
    document.put("generatedAt", NOW.plusSeconds(revision).toString());
    document.put("flags", Map.of("release", flag));
    ObjectNode root = (ObjectNode) OBJECT_MAPPER.valueToTree(document);
    String withoutChecksum =
        new JsonCanonicalizer(OBJECT_MAPPER.writeValueAsString(root)).getEncodedString();
    String checksum =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(withoutChecksum.getBytes(StandardCharsets.UTF_8)));
    root.put("checksum", checksum);
    return new Snapshot(
        new JsonCanonicalizer(OBJECT_MAPPER.writeValueAsString(root)).getEncodedString(), checksum);
  }

  private static String eventJson(UUID eventId, long revision, String checksum) throws Exception {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("eventId", eventId.toString());
    event.put("eventType", ConfigRevisionPublishedEvent.EVENT_TYPE);
    event.put("schemaVersion", 1);
    event.put("occurredAt", NOW.plusSeconds(revision).toString());
    event.put("organizationId", ORGANIZATION_ID.toString());
    event.put("projectId", PROJECT_ID.toString());
    event.put("environmentId", ENVIRONMENT_ID.toString());
    event.put("revision", revision);
    event.put("snapshotChecksum", checksum);
    event.put("traceId", UUID.randomUUID().toString());
    return OBJECT_MAPPER.writeValueAsString(event);
  }

  private static void proveSdkLastKnownGood(ConfigurableApplicationContext edge) throws Exception {
    Integer port = edge.getEnvironment().getProperty("local.server.port", Integer.class);
    try (LaunchForgeClient client =
        LaunchForgeClient.builder()
            .baseUri(URI.create("http://127.0.0.1:" + port))
            .sdkKey(credential.credential())
            .streaming(false)
            .pollingInterval(Duration.ofSeconds(30), Duration.ofSeconds(30))
            .blockingBootstrap(Duration.ofSeconds(5))
            .build()) {
      assertTrue(
          client.boolVariation("release", EvaluationContext.builder("subject-1").build(), false));
      edge.close();
      assertTrue(
          client.boolVariation("release", EvaluationContext.builder("subject-1").build(), false));
    }
  }

  private static void proveDistributedAbuseControls(
      ConfigurableApplicationContext first, ConfigurableApplicationContext second) {
    EdgeRateLimiter firstRateLimiter = first.getBean(EdgeRateLimiter.class);
    EdgeRateLimiter secondRateLimiter = second.getBean(EdgeRateLimiter.class);
    for (int request = 0; request < 30; request++) {
      assertTrue(firstRateLimiter.check(EdgeRateLimiter.Policy.STREAM, SDK_KEY_ID).permitted());
    }
    assertFalse(secondRateLimiter.check(EdgeRateLimiter.Policy.STREAM, SDK_KEY_ID).permitted());

    StreamConnectionLimiter firstConnections = first.getBean(StreamConnectionLimiter.class);
    StreamConnectionLimiter secondConnections = second.getBean(StreamConnectionLimiter.class);
    List<StreamConnectionLimiter.Lease> leases = new java.util.ArrayList<>();
    try {
      for (int connection = 0; connection < 5; connection++) {
        leases.add(firstConnections.acquire(SDK_KEY_ID));
      }
      assertThrows(
          ConnectionLimitExceededException.class, () -> secondConnections.acquire(SDK_KEY_ID));
    } finally {
      leases.forEach(StreamConnectionLimiter.Lease::close);
    }
  }

  private static long currentRevision(ConfigurableApplicationContext edge) {
    return edge.getBean(EdgeRepository.class).findCurrentRevision(ENVIRONMENT_ID).orElseThrow();
  }

  private static long currentSnapshotRevision(ConfigurableApplicationContext edge) {
    return edge.getBean(EdgeRepository.class)
        .findCurrentSnapshot(ENVIRONMENT_ID)
        .orElseThrow()
        .revision();
  }

  private static long redisRevision() {
    try {
      String value =
          REDIS
              .execInContainer(
                  "redis-cli", "HGET", RedisSnapshotMaterializer.key(ENVIRONMENT_ID), "revision")
              .getStdout()
              .trim();
      return value.isEmpty() ? 0 : Long.parseLong(value);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while reading Redis", exception);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("Unable to read Redis", exception);
    }
  }

  private static String outboxStatus(UUID eventId) {
    return jdbc.queryForObject(
        "SELECT status FROM outbox_events WHERE id = ?", String.class, eventId);
  }

  private static int outboxAttempts(UUID eventId) {
    return jdbc.queryForObject(
        "SELECT attempt_count FROM outbox_events WHERE id = ?", Integer.class, eventId);
  }

  private static void await(BooleanSupplier condition, Duration timeout) throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() >= deadline) {
        throw new AssertionError("Condition was not met within " + timeout);
      }
      TimeUnit.MILLISECONDS.sleep(50);
    }
  }

  private static void close(ConfigurableApplicationContext context) {
    if (context != null) {
      context.close();
    }
  }

  private record Snapshot(String canonical, String checksum) {}

  private record Revision(UUID eventId, String eventJson) {}
}
