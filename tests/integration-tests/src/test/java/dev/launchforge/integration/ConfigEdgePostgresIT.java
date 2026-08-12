package dev.launchforge.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.launchforge.contracts.sdk.ServerSdkKeyCredential;
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
import org.erdtman.jcs.JsonCanonicalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ConfigEdgePostgresIT extends AbstractConfigEdgeIntegrationTest {
  private static final UUID ORGANIZATION_ID =
      UUID.fromString("51000000-0000-0000-0000-000000000001");
  private static final UUID PROJECT_ID = UUID.fromString("52000000-0000-0000-0000-000000000001");
  private static final UUID ENVIRONMENT_ID =
      UUID.fromString("53000000-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");
  private static final String BROWSER_CLIENT_KEY = "lf_client_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
  private static final String BROWSER_ORIGIN = "https://storefront.example";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;
  @LocalServerPort private int port;

  private WebTestClient webTestClient;
  private ServerSdkKeyCredential.Generated credential;

  @BeforeEach
  void seedPublishedEnvironment() throws Exception {
    webTestClient =
        WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + port)
            .responseTimeout(Duration.ofSeconds(3))
            .build();
    jdbcTemplate.execute("TRUNCATE TABLE organizations CASCADE");
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, slug, name, status, version, created_at, updated_at)
        VALUES (?, 'edge-tenant', 'Edge Tenant', 'ACTIVE', 0, ?, ?)
        """,
        ORGANIZATION_ID,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
    jdbcTemplate.update(
        """
        INSERT INTO projects
          (id, organization_id, project_key, name, status, version, created_at, updated_at)
        VALUES (?, ?, 'storefront', 'Storefront', 'ACTIVE', 0, ?, ?)
        """,
        PROJECT_ID,
        ORGANIZATION_ID,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
    jdbcTemplate.update(
        """
        INSERT INTO environments
          (id, organization_id, project_id, environment_key, name, kind, status,
           current_revision, version, created_at, updated_at)
        VALUES (?, ?, ?, 'development', 'Development', 'DEVELOPMENT', 'ACTIVE', 1, 1, ?, ?)
        """,
        ENVIRONMENT_ID,
        ORGANIZATION_ID,
        PROJECT_ID,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
    insertRevision(1, true);
    credential =
        ServerSdkKeyCredential.generate(
            new SecureRandom(), "v1", TEST_PEPPER.getBytes(StandardCharsets.UTF_8));
    jdbcTemplate.update(
        """
        INSERT INTO sdk_keys
          (id, organization_id, project_id, environment_id, key_type, name, lookup_id,
           secret_verifier, pepper_version, fingerprint, status, created_at,
           created_by_issuer, created_by_subject)
        VALUES (?, ?, ?, ?, 'SERVER', 'Integration server', ?, ?, 'v1', ?, 'ACTIVE', ?,
                'https://identity.example', 'edge-test')
        """,
        UUID.randomUUID(),
        ORGANIZATION_ID,
        PROJECT_ID,
        ENVIRONMENT_ID,
        credential.lookupId(),
        credential.verifier(),
        credential.fingerprint(),
        Timestamp.from(NOW));
    jdbcTemplate.update(
        """
        INSERT INTO browser_client_keys
          (id, organization_id, project_id, environment_id, name, client_key, fingerprint,
           allowed_origins, status, created_at, created_by_issuer, created_by_subject)
        VALUES (?, ?, ?, ?, 'Integration browser', ?, '0123456789abcdef01234567',
                CAST(? AS jsonb), 'ACTIVE', ?, 'https://identity.example', 'edge-test')
        """,
        UUID.randomUUID(),
        ORGANIZATION_ID,
        PROJECT_ID,
        ENVIRONMENT_ID,
        BROWSER_CLIENT_KEY,
        "[\"" + BROWSER_ORIGIN + "\"]",
        Timestamp.from(NOW));
  }

  @Test
  void scopedAuthenticationSnapshotHeadersConditionalGetAndDenialsWork() throws Exception {
    String body =
        webTestClient
            .get()
            .uri("/sdk/v1/snapshot")
            .header(HttpHeaders.AUTHORIZATION, authorization())
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals("X-LaunchForge-Revision", "1")
            .expectHeader()
            .valueEquals("X-LaunchForge-Schema-Version", "1")
            .expectHeader()
            .exists("X-LaunchForge-Checksum")
            .expectHeader()
            .exists(HttpHeaders.ETAG)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    assertEquals(1, objectMapper.readTree(body).get("revision").asLong());
    assertTrue(objectMapper.readTree(body).get("flags").get("release").get("enabled").asBoolean());
  }

  @Test
  void conditionalSnapshotAndManagementCookieCannotSubstituteForSdkKey() {
    String etag =
        webTestClient
            .get()
            .uri("/sdk/v1/snapshot")
            .header(HttpHeaders.AUTHORIZATION, authorization())
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(String.class)
            .getResponseHeaders()
            .getETag();
    webTestClient
        .get()
        .uri("/sdk/v1/snapshot")
        .header(HttpHeaders.AUTHORIZATION, authorization())
        .header(HttpHeaders.IF_NONE_MATCH, etag)
        .exchange()
        .expectStatus()
        .isNotModified()
        .expectBody()
        .isEmpty();
    webTestClient
        .get()
        .uri("/sdk/v1/snapshot")
        .cookie("launchforge_session", "management-session-is-not-an-sdk-key")
        .exchange()
        .expectStatus()
        .isUnauthorized();

    jdbcTemplate.update(
        "UPDATE sdk_keys SET status = 'REVOKED', revoked_at = ?, revoked_by_issuer = 'test', revoked_by_subject = 'test'",
        Timestamp.from(NOW.plusSeconds(1)));
    webTestClient
        .get()
        .uri("/sdk/v1/snapshot")
        .header(HttpHeaders.AUTHORIZATION, authorization())
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void browserClientKeyGetsExactCorsAndOnlyClientVisibleProjection() throws Exception {
    String browserBody =
        webTestClient
            .get()
            .uri("/sdk/v1/client/" + BROWSER_CLIENT_KEY + "/snapshot")
            .header(HttpHeaders.ORIGIN, BROWSER_ORIGIN)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, BROWSER_ORIGIN)
            .expectHeader()
            .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    assertFalse(objectMapper.readTree(browserBody).get("flags").has("release"));
    assertTrue(objectMapper.readTree(browserBody).get("flags").has("browser-release"));

    webTestClient
        .get()
        .uri("/sdk/v1/client/" + BROWSER_CLIENT_KEY + "/snapshot")
        .header(HttpHeaders.ORIGIN, "https://evil.example")
        .exchange()
        .expectStatus()
        .isForbidden();

    webTestClient
        .get()
        .uri("/sdk/v1/snapshot")
        .header(HttpHeaders.AUTHORIZATION, "LF-SDK " + BROWSER_CLIENT_KEY)
        .exchange()
        .expectStatus()
        .isUnauthorized();

    webTestClient
        .get()
        .uri("/sdk/v1/client/" + credential.credential() + "/snapshot")
        .header(HttpHeaders.ORIGIN, BROWSER_ORIGIN)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void sdkConvergesThroughRevisionStreamAndRetainsKillSwitchAfterRevocation() throws Exception {
    URI edge = URI.create("http://127.0.0.1:" + port);
    EvaluationContext context = EvaluationContext.builder("customer-123").build();
    try (LaunchForgeClient client =
        LaunchForgeClient.builder()
            .baseUri(edge)
            .sdkKey(credential.credential())
            .streaming(true)
            .pollingInterval(Duration.ofMillis(100), Duration.ofMillis(100))
            .streamReconnectBackoff(Duration.ofMillis(25), Duration.ofMillis(100))
            .blockingBootstrap(Duration.ofSeconds(3))
            .build()) {
      assertTrue(client.boolVariation("release", context, false));

      insertRevision(2, false);
      jdbcTemplate.update(
          "UPDATE environments SET current_revision = 2, version = version + 1, updated_at = ? WHERE id = ?",
          Timestamp.from(NOW.plusSeconds(1)),
          ENVIRONMENT_ID);
      await(() -> client.currentRevision().orElse(0) == 2, Duration.ofSeconds(3));
      assertFalse(client.boolVariation("release", context, true));

      jdbcTemplate.update(
          """
          UPDATE sdk_keys SET status = 'REVOKED', expires_at = ?, revoked_at = ?,
                 revoked_by_issuer = 'test', revoked_by_subject = 'test'
          """,
          Timestamp.from(NOW.plusSeconds(2)),
          Timestamp.from(NOW.plusSeconds(2)));
      TimeUnit.MILLISECONDS.sleep(150);
      assertFalse(client.refreshAsync().get(2, TimeUnit.SECONDS));
      assertEquals(2, client.currentRevision().orElseThrow());
      assertFalse(client.boolVariation("release", context, true));
    }
  }

  private void insertRevision(long revision, boolean enabled) throws Exception {
    Snapshot snapshot = snapshot(revision, enabled);
    jdbcTemplate.update(
        """
        INSERT INTO environment_revisions
          (organization_id, project_id, environment_id, revision, schema_version, snapshot_json,
           canonical_snapshot, checksum_sha256, actor_issuer, actor_subject, created_at)
        VALUES (?, ?, ?, ?, 1, CAST(? AS jsonb), ?, ?, 'https://identity.example', 'edge-test', ?)
        """,
        ORGANIZATION_ID,
        PROJECT_ID,
        ENVIRONMENT_ID,
        revision,
        snapshot.canonical(),
        snapshot.canonical(),
        snapshot.checksum(),
        Timestamp.from(NOW.plusSeconds(revision - 1)));
  }

  private Snapshot snapshot(long revision, boolean enabled) throws Exception {
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
    document.put("environmentKey", "development");
    document.put("revision", revision);
    document.put("generatedAt", NOW.plusSeconds(revision - 1).toString());
    Map<String, Object> browserFlag = new LinkedHashMap<>(flag);
    browserFlag.put("clientVisible", true);
    document.put("flags", Map.of("release", flag, "browser-release", browserFlag));
    ObjectNode root = (ObjectNode) objectMapper.valueToTree(document);
    String withoutChecksum =
        new JsonCanonicalizer(objectMapper.writeValueAsString(root)).getEncodedString();
    String checksum =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(withoutChecksum.getBytes(StandardCharsets.UTF_8)));
    root.put("checksum", checksum);
    String canonical =
        new JsonCanonicalizer(objectMapper.writeValueAsString(root)).getEncodedString();
    return new Snapshot(canonical, checksum);
  }

  private String authorization() {
    return "LF-SDK " + credential.credential();
  }

  private static void await(BooleanSupplier condition, Duration timeout) throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() >= deadline) {
        throw new AssertionError("Condition was not met within " + timeout);
      }
      TimeUnit.MILLISECONDS.sleep(20);
    }
  }

  private record Snapshot(String canonical, String checksum) {}
}
