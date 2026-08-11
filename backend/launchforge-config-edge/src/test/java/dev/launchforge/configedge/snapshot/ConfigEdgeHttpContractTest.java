package dev.launchforge.configedge.snapshot;

import dev.launchforge.configedge.configuration.ConfigEdgeProperties;
import dev.launchforge.configedge.configuration.SdkKeyPepperProperties;
import dev.launchforge.configedge.persistence.EdgeRepository;
import dev.launchforge.configedge.persistence.EdgeRepository.StoredSnapshot;
import dev.launchforge.configedge.security.SdkAuthenticationService;
import dev.launchforge.configedge.security.SdkAuthenticationWebFilter;
import dev.launchforge.configedge.web.EdgeExceptionHandler;
import dev.launchforge.contracts.sdk.ServerSdkKeyCredential;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
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

class ConfigEdgeHttpContractTest {
  private static final String PEPPER = "test-pepper-that-is-at-least-thirty-two-bytes";
  private WebTestClient client;
  private ServerSdkKeyCredential.Generated credential;

  @BeforeEach
  void configureClient() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    ConfigEdgeProperties properties =
        new ConfigEdgeProperties(1024 * 1024, Duration.ofSeconds(1), Duration.ofSeconds(15), 10, 2);
    credential =
        ServerSdkKeyCredential.generate(
            new SecureRandom(), "v1", PEPPER.getBytes(StandardCharsets.UTF_8));
    FakeRepository repository = new FakeRepository(credential, snapshot(objectMapper));
    SdkAuthenticationService authentication =
        new SdkAuthenticationService(
            repository, new SdkKeyPepperProperties(Map.of("v1", PEPPER)), Clock.systemUTC());
    SnapshotController controller =
        new SnapshotController(repository, new SnapshotIntegrityVerifier(objectMapper, properties));
    client =
        WebTestClient.bindToController(controller)
            .controllerAdvice(new EdgeExceptionHandler())
            .webFilter(new SdkAuthenticationWebFilter(authentication))
            .build();
  }

  @Test
  void authenticatedGetReturnsRuntimeSnapshotHeadersAndConditional304() {
    String authorization = "LF-SDK " + credential.credential();
    String etag =
        client
            .get()
            .uri("/sdk/v1/snapshot")
            .header(HttpHeaders.AUTHORIZATION, authorization)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals(SnapshotController.REVISION_HEADER, "1")
            .expectHeader()
            .valueEquals(SnapshotController.SCHEMA_VERSION_HEADER, "1")
            .expectHeader()
            .exists(SnapshotController.CHECKSUM_HEADER)
            .expectBody()
            .jsonPath("$.revision")
            .isEqualTo(1)
            .returnResult()
            .getResponseHeaders()
            .getETag();

    client
        .get()
        .uri("/sdk/v1/snapshot")
        .header(HttpHeaders.AUTHORIZATION, authorization)
        .header(HttpHeaders.IF_NONE_MATCH, etag)
        .exchange()
        .expectStatus()
        .isNotModified()
        .expectBody()
        .isEmpty();
  }

  @Test
  void absentSdkAuthorizationIsDeniedEvenWhenManagementCookieIsPresent() {
    client
        .get()
        .uri("/sdk/v1/snapshot")
        .cookie("launchforge_session", "not-an-sdk-key")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("SDK_KEY_INVALID");
  }

  private static StoredSnapshot snapshot(ObjectMapper objectMapper) throws Exception {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schemaVersion", 1);
    document.put("algorithmVersion", 1);
    document.put("projectKey", "storefront");
    document.put("environmentKey", "development");
    document.put("revision", 1);
    document.put("generatedAt", "2026-08-11T12:00:00Z");
    document.put("flags", Map.of());
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
    return new StoredSnapshot(UUID.randomUUID(), 1, 1, canonical, checksum);
  }

  private static final class FakeRepository implements EdgeRepository {
    private final UUID keyId = UUID.randomUUID();
    private final ServerSdkKeyCredential.Generated credential;
    private final StoredSnapshot snapshot;

    FakeRepository(ServerSdkKeyCredential.Generated credential, StoredSnapshot snapshot) {
      this.credential = credential;
      this.snapshot = snapshot;
    }

    @Override
    public Optional<StoredSdkCredential> findCredential(String lookupId) {
      return credential.lookupId().equals(lookupId)
          ? Optional.of(
              new StoredSdkCredential(
                  keyId,
                  snapshot.environmentId(),
                  credential.verifier(),
                  "v1",
                  "ACTIVE",
                  null,
                  true))
          : Optional.empty();
    }

    @Override
    public Optional<CredentialLifecycle> findLifecycle(UUID ignored) {
      return Optional.of(new CredentialLifecycle(snapshot.environmentId(), "ACTIVE", null, true));
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
