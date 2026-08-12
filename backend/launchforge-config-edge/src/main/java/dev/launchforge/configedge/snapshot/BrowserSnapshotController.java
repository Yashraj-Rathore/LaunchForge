package dev.launchforge.configedge.snapshot;

import dev.launchforge.configedge.persistence.EdgeRepository;
import dev.launchforge.configedge.security.BrowserClientScope;
import dev.launchforge.configedge.security.BrowserClientWebFilter;
import dev.launchforge.configedge.snapshot.SnapshotIntegrityVerifier.VerifiedSnapshot;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public final class BrowserSnapshotController {
  private final EdgeRepository repository;
  private final BrowserSnapshotProjector projector;

  public BrowserSnapshotController(EdgeRepository repository, BrowserSnapshotProjector projector) {
    this.repository = repository;
    this.projector = projector;
  }

  @GetMapping(
      value = "/sdk/v1/client/{clientKey}/snapshot",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<String>> snapshot(
      ServerWebExchange exchange,
      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
          List<String> ifNoneMatch) {
    BrowserClientScope scope = scope(exchange);
    return Mono.fromCallable(
            () ->
                repository
                    .findCurrentSnapshot(scope.environmentId())
                    .map(projector::project)
                    .orElseThrow(SnapshotUnavailableException::new))
        .subscribeOn(Schedulers.boundedElastic())
        .map(snapshot -> response(snapshot, ifNoneMatch));
  }

  private static ResponseEntity<String> response(
      VerifiedSnapshot snapshot, List<String> ifNoneMatch) {
    boolean unchanged =
        ifNoneMatch != null && ifNoneMatch.stream().anyMatch(snapshot.etag()::equals);
    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(unchanged ? HttpStatus.NOT_MODIFIED : HttpStatus.OK)
            .contentType(MediaType.APPLICATION_JSON)
            .cacheControl(CacheControl.noStore())
            .eTag(snapshot.etag())
            .header(SnapshotController.REVISION_HEADER, Long.toString(snapshot.revision()))
            .header(SnapshotController.CHECKSUM_HEADER, snapshot.checksum())
            .header(
                SnapshotController.SCHEMA_VERSION_HEADER,
                Integer.toString(snapshot.schemaVersion()));
    return unchanged ? response.build() : response.body(snapshot.canonicalJson());
  }

  private static BrowserClientScope scope(ServerWebExchange exchange) {
    BrowserClientScope scope = exchange.getAttribute(BrowserClientWebFilter.SCOPE_ATTRIBUTE);
    if (scope == null) {
      throw new IllegalStateException("Browser client scope is missing");
    }
    return scope;
  }
}
