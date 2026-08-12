package dev.launchforge.configedge.stream;

import dev.launchforge.configedge.configuration.ConfigEdgeProperties;
import dev.launchforge.configedge.persistence.EdgeRepository;
import dev.launchforge.configedge.security.BrowserClientAuthenticationService;
import dev.launchforge.configedge.security.BrowserClientScope;
import dev.launchforge.configedge.security.BrowserClientWebFilter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public final class BrowserRevisionStreamController {
  private final EdgeRepository repository;
  private final BrowserClientAuthenticationService authenticationService;
  private final StreamConnectionLimiter connectionLimiter;
  private final Duration revisionPollInterval;
  private final Duration heartbeatInterval;

  public BrowserRevisionStreamController(
      EdgeRepository repository,
      BrowserClientAuthenticationService authenticationService,
      StreamConnectionLimiter connectionLimiter,
      ConfigEdgeProperties properties) {
    this.repository = repository;
    this.authenticationService = authenticationService;
    this.connectionLimiter = connectionLimiter;
    this.revisionPollInterval = properties.revisionPollInterval();
    this.heartbeatInterval = properties.heartbeatInterval();
  }

  @GetMapping(
      value = "/sdk/v1/client/{clientKey}/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<RevisionNotification>> stream(
      ServerWebExchange exchange,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
    BrowserClientScope scope = exchange.getAttribute(BrowserClientWebFilter.SCOPE_ATTRIBUTE);
    if (scope == null) {
      return Flux.error(new IllegalStateException("Browser client scope is missing"));
    }
    long lastSeen = lastRevision(lastEventId);
    return Flux.using(
        () -> connectionLimiter.acquire(scope.keyId()),
        ignored -> Flux.merge(revisions(scope, lastSeen), heartbeats()),
        StreamConnectionLimiter.Lease::close);
  }

  private Flux<ServerSentEvent<RevisionNotification>> revisions(
      BrowserClientScope scope, long lastSeen) {
    AtomicLong delivered = new AtomicLong(lastSeen);
    AtomicBoolean first = new AtomicBoolean(true);
    return Flux.interval(Duration.ZERO, revisionPollInterval)
        .onBackpressureDrop()
        .concatMap(
            ignored ->
                Mono.fromCallable(
                        () -> {
                          authenticationService.revalidate(scope);
                          return repository.findCurrentRevision(scope.environmentId());
                        })
                    .subscribeOn(Schedulers.boundedElastic()))
        .handle(
            (revision, sink) -> {
              if (revision.isEmpty() || revision.getAsLong() <= 0) {
                return;
              }
              long current = revision.getAsLong();
              if (first.compareAndSet(true, false) && current < delivered.get()) {
                delivered.set(current);
              }
              long previous = delivered.get();
              if (current > previous && delivered.compareAndSet(previous, current)) {
                sink.next(
                    ServerSentEvent.builder(new RevisionNotification(current))
                        .event("revision")
                        .id(Long.toString(current))
                        .build());
              }
            });
  }

  private Flux<ServerSentEvent<RevisionNotification>> heartbeats() {
    return Flux.interval(heartbeatInterval)
        .map(
            ignored ->
                ServerSentEvent.<RevisionNotification>builder()
                    .comment("launchforge-heartbeat")
                    .build());
  }

  private static long lastRevision(String value) {
    if (value == null || value.isBlank()) {
      return 0;
    }
    try {
      long revision = Long.parseLong(value);
      if (revision < 0) {
        throw new NumberFormatException("negative");
      }
      return revision;
    } catch (NumberFormatException exception) {
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Last-Event-ID is invalid");
    }
  }

  public record RevisionNotification(long revision) {}
}
