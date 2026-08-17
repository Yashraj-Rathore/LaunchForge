package dev.launchforge.configedge.snapshot;

import dev.launchforge.configedge.observability.SnapshotMetrics;
import dev.launchforge.configedge.persistence.EdgeRepository;
import dev.launchforge.configedge.security.SdkAuthenticationWebFilter;
import dev.launchforge.configedge.security.SdkCredentialScope;
import dev.launchforge.configedge.snapshot.SnapshotIntegrityVerifier.VerifiedSnapshot;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public final class SnapshotController {
  public static final String REVISION_HEADER = "X-LaunchForge-Revision";
  public static final String CHECKSUM_HEADER = "X-LaunchForge-Checksum";
  public static final String SCHEMA_VERSION_HEADER = "X-LaunchForge-Schema-Version";

  private final EdgeRepository repository;
  private final SnapshotIntegrityVerifier verifier;
  private final SnapshotMetrics metrics;

  public SnapshotController(
      EdgeRepository repository, SnapshotIntegrityVerifier verifier, SnapshotMetrics metrics) {
    this.repository = repository;
    this.verifier = verifier;
    this.metrics = metrics;
  }

  @GetMapping(value = "/sdk/v1/snapshot", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<String>> snapshot(
      ServerWebExchange exchange,
      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
          List<String> ifNoneMatch) {
    SdkCredentialScope scope = scope(exchange);
    Mono<ResponseEntity<String>> operation =
        Mono.fromCallable(
                () ->
                    repository
                        .findCurrentSnapshot(scope.environmentId())
                        .map(verifier::verify)
                        .orElseThrow(SnapshotUnavailableException::new))
            .subscribeOn(Schedulers.boundedElastic())
            .map(snapshot -> response(snapshot, ifNoneMatch));
    return metrics.observe("server", operation);
  }

  private static ResponseEntity<String> response(
      VerifiedSnapshot snapshot, List<String> ifNoneMatch) {
    boolean unchanged =
        ifNoneMatch != null && ifNoneMatch.stream().anyMatch(snapshot.etag()::equals);
    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(
                unchanged
                    ? org.springframework.http.HttpStatus.NOT_MODIFIED
                    : org.springframework.http.HttpStatus.OK)
            .contentType(MediaType.APPLICATION_JSON)
            .cacheControl(CacheControl.noStore())
            .eTag(snapshot.etag())
            .header(REVISION_HEADER, Long.toString(snapshot.revision()))
            .header(CHECKSUM_HEADER, snapshot.checksum())
            .header(SCHEMA_VERSION_HEADER, Integer.toString(snapshot.schemaVersion()));
    if (unchanged) {
      return response.build();
    }
    return response.body(snapshot.canonicalJson());
  }

  private static SdkCredentialScope scope(ServerWebExchange exchange) {
    SdkCredentialScope scope = exchange.getAttribute(SdkAuthenticationWebFilter.SCOPE_ATTRIBUTE);
    if (scope == null) {
      throw SdkAuthenticationExceptionBridge.missingScope();
    }
    return scope;
  }

  private static final class SdkAuthenticationExceptionBridge {
    private SdkAuthenticationExceptionBridge() {}

    static IllegalStateException missingScope() {
      return new IllegalStateException("SDK authentication scope is missing");
    }
  }
}
