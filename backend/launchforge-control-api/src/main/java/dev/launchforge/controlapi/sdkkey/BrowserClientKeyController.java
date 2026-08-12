package dev.launchforge.controlapi.sdkkey;

import dev.launchforge.application.sdkkey.BrowserClientKeyService;
import dev.launchforge.controlapi.security.OperatorIdentityResolver;
import dev.launchforge.domain.controlplane.EnvironmentId;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.sdkkey.BrowserClientKey;
import dev.launchforge.domain.sdkkey.SdkKeyId;
import dev.launchforge.domain.sdkkey.SdkKeyStatus;
import java.net.URI;
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
public final class BrowserClientKeyController {
  private final OperatorIdentityResolver identityResolver;
  private final BrowserClientKeyService service;

  public BrowserClientKeyController(
      OperatorIdentityResolver identityResolver, BrowserClientKeyService service) {
    this.identityResolver = identityResolver;
    this.service = service;
  }

  @PostMapping("/environments/{environmentId}/client-keys")
  public ResponseEntity<BrowserClientKeyResponse> create(
      Authentication authentication,
      @PathVariable UUID environmentId,
      @RequestBody CreateBrowserClientKeyRequest request) {
    BrowserClientKey created =
        service.create(
            actor(authentication),
            new EnvironmentId(environmentId),
            request.name(),
            request.allowedOrigins(),
            request.expiresAt());
    return ResponseEntity.created(URI.create("/api/v1/client-keys/" + created.id().value()))
        .body(BrowserClientKeyResponse.from(created));
  }

  @GetMapping("/environments/{environmentId}/client-keys")
  public List<BrowserClientKeyResponse> list(
      Authentication authentication, @PathVariable UUID environmentId) {
    return service.list(actor(authentication), new EnvironmentId(environmentId)).stream()
        .map(BrowserClientKeyResponse::from)
        .toList();
  }

  @PostMapping("/client-keys/{keyId}/revoke")
  public ResponseEntity<Void> revoke(Authentication authentication, @PathVariable UUID keyId) {
    service.revoke(actor(authentication), new SdkKeyId(keyId));
    return ResponseEntity.noContent().build();
  }

  private OidcIdentity actor(Authentication authentication) {
    return identityResolver.requireIdentity(authentication);
  }

  public record CreateBrowserClientKeyRequest(
      String name, List<String> allowedOrigins, Instant expiresAt) {}

  public record BrowserClientKeyResponse(
      UUID id,
      UUID environmentId,
      String name,
      String clientKey,
      String fingerprint,
      List<String> allowedOrigins,
      SdkKeyStatus status,
      Instant expiresAt,
      Instant createdAt,
      Instant lastUsedAt,
      Instant revokedAt) {
    static BrowserClientKeyResponse from(BrowserClientKey key) {
      return new BrowserClientKeyResponse(
          key.id().value(),
          key.environmentId().value(),
          key.name(),
          key.clientKey(),
          key.fingerprint(),
          key.allowedOrigins(),
          key.status(),
          key.expiresAt(),
          key.createdAt(),
          key.lastUsedAt(),
          key.revokedAt());
    }
  }
}
