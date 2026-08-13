package dev.launchforge.configedge.analytics;

import dev.launchforge.configedge.analytics.AnalyticsIngestionService.AcceptedBatch;
import dev.launchforge.configedge.security.BrowserClientScope;
import dev.launchforge.configedge.security.BrowserClientWebFilter;
import dev.launchforge.configedge.security.SdkAuthenticationWebFilter;
import dev.launchforge.configedge.security.SdkCredentialScope;
import dev.launchforge.contracts.events.IngestedEvaluationBatch.Source;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@ConditionalOnProperty(name = "launchforge.analytics.ingestion.enabled", havingValue = "true")
public final class AnalyticsIngestionController {
  private final AnalyticsIngestionService service;

  public AnalyticsIngestionController(AnalyticsIngestionService service) {
    this.service = service;
  }

  @PostMapping(
      value = "/events/v1/evaluations/batch",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<AcceptedBatch>> serverBatch(
      ServerWebExchange exchange, @RequestBody byte[] body) {
    SdkCredentialScope scope = exchange.getAttribute(SdkAuthenticationWebFilter.SCOPE_ATTRIBUTE);
    if (scope == null) {
      throw new IllegalStateException("SDK authentication scope is missing");
    }
    return accepted(scope.keyId(), scope.environmentId(), Source.SERVER, body);
  }

  @PostMapping(
      value = "/events/v1/client/{clientKey}/evaluations/batch",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<AcceptedBatch>> browserBatch(
      ServerWebExchange exchange, @PathVariable String clientKey, @RequestBody byte[] body) {
    BrowserClientScope scope = exchange.getAttribute(BrowserClientWebFilter.SCOPE_ATTRIBUTE);
    if (scope == null) {
      throw new IllegalStateException("Browser client authentication scope is missing");
    }
    return accepted(scope.keyId(), scope.environmentId(), Source.BROWSER, body);
  }

  private Mono<ResponseEntity<AcceptedBatch>> accepted(
      java.util.UUID keyId, java.util.UUID environmentId, Source source, byte[] body) {
    return Mono.fromCallable(() -> service.ingest(keyId, environmentId, source, body))
        .subscribeOn(Schedulers.boundedElastic())
        .map(value -> ResponseEntity.status(HttpStatus.ACCEPTED).body(value));
  }
}
