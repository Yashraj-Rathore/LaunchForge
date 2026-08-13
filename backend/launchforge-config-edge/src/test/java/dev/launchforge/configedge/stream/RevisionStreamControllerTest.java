package dev.launchforge.configedge.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.launchforge.configedge.configuration.ConfigEdgeProperties;
import dev.launchforge.configedge.configuration.SdkKeyPepperProperties;
import dev.launchforge.configedge.persistence.EdgeRepository;
import dev.launchforge.configedge.security.SdkAuthenticationException;
import dev.launchforge.configedge.security.SdkAuthenticationService;
import dev.launchforge.configedge.security.SdkAuthenticationWebFilter;
import dev.launchforge.configedge.security.SdkCredentialScope;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class RevisionStreamControllerTest {
  private static final String PEPPER = "test-pepper-that-is-at-least-thirty-two-bytes";

  @Test
  void emitsOnlyNewerRevisionEventsWithValidSseShape() {
    Fixture fixture = fixture(2);

    StepVerifier.create(fixture.stream("1").filter(event -> event.data() != null).take(1))
        .assertNext(
            event -> {
              assertEquals("revision", event.event());
              assertEquals("2", event.id());
              assertEquals(2, event.data().revision());
            })
        .verifyComplete();
    assertEquals(0, fixture.limiter().activeConnections());
  }

  @Test
  void staleRevisionIsIgnoredAndRevokedKeyTerminatesTheStream() {
    Fixture fixture = fixture(1);
    fixture.repository().status = "REVOKED";

    StepVerifier.create(fixture.stream("5").filter(event -> event.data() != null))
        .expectError(SdkAuthenticationException.class)
        .verify(Duration.ofSeconds(1));
    assertEquals(0, fixture.limiter().activeConnections());
  }

  private static Fixture fixture(long revision) {
    FakeRepository repository = new FakeRepository(revision);
    ConfigEdgeProperties properties =
        new ConfigEdgeProperties(1024 * 1024, Duration.ofMillis(5), Duration.ofSeconds(1), 4, 2);
    SdkAuthenticationService authentication =
        new SdkAuthenticationService(
            repository,
            new SdkKeyPepperProperties(Map.of("v1", PEPPER)),
            Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC));
    StreamConnectionLimiter limiter =
        new StreamConnectionLimiter(properties, new SimpleMeterRegistry());
    RevisionSignalBus signalBus =
        new RevisionSignalBus(new ObjectMapper(), new SimpleMeterRegistry());
    return new Fixture(
        repository,
        limiter,
        new RevisionStreamController(repository, authentication, limiter, signalBus, properties));
  }

  private record Fixture(
      FakeRepository repository,
      StreamConnectionLimiter limiter,
      RevisionStreamController controller) {
    reactor.core.publisher.Flux<ServerSentEvent<RevisionStreamController.RevisionNotification>>
        stream(String lastEventId) {
      MockServerWebExchange exchange =
          MockServerWebExchange.from(MockServerHttpRequest.get("/sdk/v1/stream").build());
      exchange
          .getAttributes()
          .put(
              SdkAuthenticationWebFilter.SCOPE_ATTRIBUTE,
              new SdkCredentialScope(repository.keyId, repository.environmentId));
      return controller.stream(exchange, lastEventId);
    }
  }

  private static final class FakeRepository implements EdgeRepository {
    private final UUID keyId = UUID.randomUUID();
    private final UUID environmentId = UUID.randomUUID();
    private final AtomicLong revision;
    private String status = "ACTIVE";

    FakeRepository(long revision) {
      this.revision = new AtomicLong(revision);
    }

    @Override
    public Optional<StoredSdkCredential> findCredential(String ignored) {
      return Optional.empty();
    }

    @Override
    public Optional<CredentialLifecycle> findLifecycle(UUID ignored) {
      return Optional.of(new CredentialLifecycle(environmentId, status, null, true));
    }

    @Override
    public Optional<StoredSnapshot> findCurrentSnapshot(UUID ignored) {
      return Optional.empty();
    }

    @Override
    public OptionalLong findCurrentRevision(UUID ignored) {
      return OptionalLong.of(revision.get());
    }

    @Override
    public void recordUse(UUID ignored) {}
  }
}
