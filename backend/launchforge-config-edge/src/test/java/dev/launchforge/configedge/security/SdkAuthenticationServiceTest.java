package dev.launchforge.configedge.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.launchforge.configedge.configuration.SdkKeyPepperProperties;
import dev.launchforge.configedge.persistence.EdgeRepository;
import dev.launchforge.contracts.sdk.ServerSdkKeyCredential;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SdkAuthenticationServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");
  private static final String PEPPER = "test-pepper-that-is-at-least-thirty-two-bytes";

  @Test
  void validKeyMapsToExactlyItsStoredEnvironment() {
    Fixture fixture = fixture("ACTIVE", NOW.plusSeconds(60), true);

    SdkCredentialScope scope =
        fixture.service().authenticate("LF-SDK " + fixture.generated().credential());

    assertEquals(fixture.repository().keyId, scope.keyId());
    assertEquals(fixture.repository().environmentId, scope.environmentId());
    assertEquals(1, fixture.repository().recordedUses);
  }

  @Test
  void malformedWrongRevokedExpiredAndDisabledKeysAreDenied() {
    Fixture active = fixture("ACTIVE", NOW.plusSeconds(60), true);
    assertThrows(
        SdkAuthenticationException.class,
        () -> active.service().authenticate("LF-SDK " + active.generated().credential() + "x"));
    assertThrows(
        SdkAuthenticationException.class,
        () -> active.service().authenticate("Bearer " + active.generated().credential()));
    assertThrows(
        SdkAuthenticationException.class, () -> fixture("REVOKED", null, true).authenticate());
    assertThrows(
        SdkAuthenticationException.class, () -> fixture("DISABLED", null, true).authenticate());
    assertThrows(
        SdkAuthenticationException.class,
        () -> fixture("ACTIVE", NOW.minusSeconds(1), true).authenticate());
  }

  @Test
  void inactiveEnvironmentIsForbiddenAndLifecycleRevalidationDisconnectsRevocation() {
    Fixture inactive = fixture("ACTIVE", null, false);
    SdkAuthenticationException forbidden =
        assertThrows(SdkAuthenticationException.class, inactive::authenticate);
    assertEquals(true, forbidden.isForbidden());

    Fixture active = fixture("ACTIVE", null, true);
    SdkCredentialScope scope = active.authenticate();
    active.repository().status = "REVOKED";
    assertThrows(SdkAuthenticationException.class, () -> active.service().revalidate(scope));
  }

  private static Fixture fixture(String status, Instant expiresAt, boolean scopeActive) {
    byte[] pepper = PEPPER.getBytes(StandardCharsets.UTF_8);
    ServerSdkKeyCredential.Generated generated =
        ServerSdkKeyCredential.generate(new SecureRandom(), "v1", pepper);
    FakeRepository repository = new FakeRepository(generated, status, expiresAt, scopeActive);
    SdkAuthenticationService service =
        new SdkAuthenticationService(
            repository,
            new SdkKeyPepperProperties(Map.of("v1", PEPPER)),
            Clock.fixed(NOW, ZoneOffset.UTC));
    return new Fixture(service, repository, generated);
  }

  private record Fixture(
      SdkAuthenticationService service,
      FakeRepository repository,
      ServerSdkKeyCredential.Generated generated) {
    SdkCredentialScope authenticate() {
      return service.authenticate("LF-SDK " + generated.credential());
    }
  }

  private static final class FakeRepository implements EdgeRepository {
    private final UUID keyId = UUID.randomUUID();
    private final UUID environmentId = UUID.randomUUID();
    private final ServerSdkKeyCredential.Generated generated;
    private final Instant expiresAt;
    private final boolean scopeActive;
    private String status;
    private int recordedUses;

    FakeRepository(
        ServerSdkKeyCredential.Generated generated,
        String status,
        Instant expiresAt,
        boolean scopeActive) {
      this.generated = generated;
      this.status = status;
      this.expiresAt = expiresAt;
      this.scopeActive = scopeActive;
    }

    @Override
    public Optional<StoredSdkCredential> findCredential(String lookupId) {
      return generated.lookupId().equals(lookupId)
          ? Optional.of(
              new StoredSdkCredential(
                  keyId, environmentId, generated.verifier(), "v1", status, expiresAt, scopeActive))
          : Optional.empty();
    }

    @Override
    public Optional<CredentialLifecycle> findLifecycle(UUID ignored) {
      return Optional.of(new CredentialLifecycle(environmentId, status, expiresAt, scopeActive));
    }

    @Override
    public Optional<StoredSnapshot> findCurrentSnapshot(UUID ignored) {
      return Optional.empty();
    }

    @Override
    public OptionalLong findCurrentRevision(UUID ignored) {
      return OptionalLong.empty();
    }

    @Override
    public void recordUse(UUID ignored) {
      recordedUses++;
    }
  }
}
