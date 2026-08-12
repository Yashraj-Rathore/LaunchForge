package dev.launchforge.configedge.snapshot;

import dev.launchforge.configedge.configuration.ConfigEdgeProperties;
import dev.launchforge.configedge.persistence.EdgeRepository;
import dev.launchforge.configedge.persistence.EdgeRepository.StoredBrowserCredential;
import dev.launchforge.configedge.persistence.EdgeRepository.StoredSnapshot;
import dev.launchforge.configedge.security.BrowserClientAuthenticationService;
import dev.launchforge.configedge.security.BrowserClientWebFilter;
import dev.launchforge.configedge.web.EdgeExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.erdtman.jcs.JsonCanonicalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class BrowserConfigEdgeHttpContractTest {
  private static final String CLIENT_KEY = "lf_client_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
  private static final String ALLOWED_ORIGIN = "https://shop.example";
  private WebTestClient client;
  private StoredSnapshot authoritative;

  @BeforeEach
  void configureClient() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    ConfigEdgeProperties properties =
        new ConfigEdgeProperties(1024 * 1024, Duration.ofSeconds(1), Duration.ofSeconds(15), 10, 2);
    authoritative = snapshot(objectMapper);
    FakeRepository repository = new FakeRepository(authoritative);
    SnapshotIntegrityVerifier verifier = new SnapshotIntegrityVerifier(objectMapper, properties);
    BrowserSnapshotController controller =
        new BrowserSnapshotController(
            repository, new BrowserSnapshotProjector(objectMapper, verifier, properties));
    BrowserClientAuthenticationService authentication =
        new BrowserClientAuthenticationService(repository, Clock.systemUTC());
    client =
        WebTestClient.bindToController(controller)
            .controllerAdvice(new EdgeExceptionHandler())
            .webFilter(new BrowserClientWebFilter(authentication))
            .build();
  }

  @Test
  void exactAllowedOriginReceivesOnlyClientVisibleFlagsAndProjectionChecksum() {
    client
        .get()
        .uri("/sdk/v1/client/" + CLIENT_KEY + "/snapshot")
        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
        .expectHeader()
        .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)
        .expectHeader()
        .value(
            SnapshotController.CHECKSUM_HEADER,
            checksum ->
                org.junit.jupiter.api.Assertions.assertNotEquals(
                    authoritative.checksum(), checksum))
        .expectBody()
        .jsonPath("$.flags['client-flag'].clientVisible")
        .isEqualTo(true)
        .jsonPath("$.flags['server-flag']")
        .doesNotExist();
  }

  @Test
  void disallowedOriginAndUnknownClientKeyAreDenied() {
    client
        .get()
        .uri("/sdk/v1/client/" + CLIENT_KEY + "/snapshot")
        .header(HttpHeaders.ORIGIN, "https://evil.example")
        .exchange()
        .expectStatus()
        .isForbidden();

    client
        .get()
        .uri("/sdk/v1/client/lf_client_BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/snapshot")
        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void preflightIsExplicitNonCredentialedAndHeaderBounded() {
    client
        .options()
        .uri("/sdk/v1/client/" + CLIENT_KEY + "/stream")
        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Last-Event-ID")
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET")
        .expectHeader()
        .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
  }

  private static StoredSnapshot snapshot(ObjectMapper objectMapper) throws Exception {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schemaVersion", 1);
    document.put("algorithmVersion", 1);
    document.put("projectKey", "storefront");
    document.put("environmentKey", "development");
    document.put("revision", 1);
    document.put("generatedAt", "2026-08-12T12:00:00Z");
    document.put(
        "flags",
        Map.of(
            "client-flag", flag(true),
            "server-flag", flag(false)));
    ObjectNode root = (ObjectNode) objectMapper.valueToTree(document);
    String withoutChecksum =
        new JsonCanonicalizer(objectMapper.writeValueAsString(root)).getEncodedString();
    String checksum = sha256(withoutChecksum);
    root.put("checksum", checksum);
    String canonical =
        new JsonCanonicalizer(objectMapper.writeValueAsString(root)).getEncodedString();
    return new StoredSnapshot(UUID.randomUUID(), 1, 1, canonical, checksum);
  }

  private static Map<String, Object> flag(boolean clientVisible) {
    return Map.of(
        "type",
        "boolean",
        "enabled",
        true,
        "clientVisible",
        clientVisible,
        "variations",
        java.util.List.of(Map.of("id", "off", "value", false), Map.of("id", "on", "value", true)),
        "offVariation",
        "off",
        "defaultVariation",
        "on",
        "rules",
        java.util.List.of());
  }

  private static String sha256(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static final class FakeRepository implements EdgeRepository {
    private final UUID keyId = UUID.randomUUID();
    private final StoredSnapshot snapshot;

    FakeRepository(StoredSnapshot snapshot) {
      this.snapshot = snapshot;
    }

    @Override
    public Optional<StoredSdkCredential> findCredential(String ignored) {
      return Optional.empty();
    }

    @Override
    public Optional<CredentialLifecycle> findLifecycle(UUID ignored) {
      return Optional.empty();
    }

    @Override
    public Optional<StoredBrowserCredential> findBrowserCredential(String candidate) {
      return CLIENT_KEY.equals(candidate)
          ? Optional.of(
              new StoredBrowserCredential(
                  keyId,
                  snapshot.environmentId(),
                  java.util.List.of(ALLOWED_ORIGIN),
                  "ACTIVE",
                  null,
                  true))
          : Optional.empty();
    }

    @Override
    public Optional<BrowserCredentialLifecycle> findBrowserLifecycle(UUID ignored) {
      return Optional.of(
          new BrowserCredentialLifecycle(snapshot.environmentId(), "ACTIVE", null, true));
    }

    @Override
    public Optional<StoredSnapshot> findCurrentSnapshot(UUID environmentId) {
      return snapshot.environmentId().equals(environmentId)
          ? Optional.of(snapshot)
          : Optional.empty();
    }

    @Override
    public OptionalLong findCurrentRevision(UUID ignored) {
      return OptionalLong.of(snapshot.revision());
    }

    @Override
    public void recordUse(UUID ignored) {}
  }
}
