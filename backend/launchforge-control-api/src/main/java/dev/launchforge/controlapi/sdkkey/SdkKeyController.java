package dev.launchforge.controlapi.sdkkey;

import dev.launchforge.application.sdkkey.IssuedServerSdkKey;
import dev.launchforge.application.sdkkey.SdkKeyService;
import dev.launchforge.controlapi.security.OperatorIdentityResolver;
import dev.launchforge.domain.controlplane.EnvironmentId;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.sdkkey.SdkKeyId;
import dev.launchforge.domain.sdkkey.SdkKeyStatus;
import dev.launchforge.domain.sdkkey.ServerSdkKey;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public final class SdkKeyController {
  private final OperatorIdentityResolver identityResolver;
  private final SdkKeyService service;

  public SdkKeyController(OperatorIdentityResolver identityResolver, SdkKeyService service) {
    this.identityResolver = identityResolver;
    this.service = service;
  }

  @PostMapping("/environments/{environmentId}/sdk-keys")
  public ResponseEntity<IssuedSdkKeyResponse> create(
      Authentication authentication,
      @PathVariable UUID environmentId,
      @RequestBody CreateSdkKeyRequest request) {
    IssuedServerSdkKey issued =
        service.create(
            actor(authentication),
            new EnvironmentId(environmentId),
            request.name(),
            request.expiresAt());
    return ResponseEntity.created(URI.create("/api/v1/sdk-keys/" + issued.metadata().id().value()))
        .body(IssuedSdkKeyResponse.from(issued));
  }

  @GetMapping("/environments/{environmentId}/sdk-keys")
  public List<SdkKeyResponse> list(
      Authentication authentication, @PathVariable UUID environmentId) {
    return service.list(actor(authentication), new EnvironmentId(environmentId)).stream()
        .map(SdkKeyResponse::from)
        .toList();
  }

  @PostMapping("/sdk-keys/{keyId}/rotate")
  public IssuedSdkKeyResponse rotate(
      Authentication authentication,
      @PathVariable UUID keyId,
      @RequestBody(required = false) RotateSdkKeyRequest request) {
    RotateSdkKeyRequest effective = request == null ? new RotateSdkKeyRequest(0, null) : request;
    IssuedServerSdkKey issued =
        service.rotate(
            actor(authentication),
            new SdkKeyId(keyId),
            Duration.ofSeconds(effective.overlapSeconds()),
            effective.expiresAt());
    return IssuedSdkKeyResponse.from(issued);
  }

  @PostMapping("/sdk-keys/{keyId}/revoke")
  public ResponseEntity<Void> revoke(Authentication authentication, @PathVariable UUID keyId) {
    service.revoke(actor(authentication), new SdkKeyId(keyId));
    return ResponseEntity.noContent().build();
  }

  private OidcIdentity actor(Authentication authentication) {
    return identityResolver.requireIdentity(authentication);
  }

  public record CreateSdkKeyRequest(String name, Instant expiresAt) {}

  public record RotateSdkKeyRequest(long overlapSeconds, Instant expiresAt) {
    public RotateSdkKeyRequest {
      if (overlapSeconds < 0
          || overlapSeconds > SdkKeyService.MAXIMUM_ROTATION_OVERLAP.toSeconds()) {
        throw new IllegalArgumentException("overlapSeconds must be between zero and 86400");
      }
    }
  }

  public record SdkKeyResponse(
      UUID id,
      UUID environmentId,
      String name,
      String fingerprint,
      SdkKeyStatus status,
      Instant expiresAt,
      Instant createdAt,
      Instant lastUsedAt,
      Instant revokedAt,
      UUID rotatedFromId) {
    static SdkKeyResponse from(ServerSdkKey key) {
      return new SdkKeyResponse(
          key.id().value(),
          key.environmentId().value(),
          key.name(),
          key.fingerprint(),
          key.status(),
          key.expiresAt(),
          key.createdAt(),
          key.lastUsedAt(),
          key.revokedAt(),
          key.rotatedFromId() == null ? null : key.rotatedFromId().value());
    }
  }

  public record IssuedSdkKeyResponse(SdkKeyResponse key, String secret) {
    static IssuedSdkKeyResponse from(IssuedServerSdkKey issued) {
      return new IssuedSdkKeyResponse(SdkKeyResponse.from(issued.metadata()), issued.credential());
    }
  }
}
