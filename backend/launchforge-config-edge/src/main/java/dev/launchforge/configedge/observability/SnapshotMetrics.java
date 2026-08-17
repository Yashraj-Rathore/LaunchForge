package dev.launchforge.configedge.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public final class SnapshotMetrics {
  private final MeterRegistry registry;

  public SnapshotMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public <T> Mono<ResponseEntity<T>> observe(
      String credentialClass, Mono<ResponseEntity<T>> operation) {
    return Mono.defer(
        () -> {
          Timer.Sample sample = Timer.start(registry);
          AtomicReference<String> outcome = new AtomicReference<>("error");
          return operation
              .doOnNext(
                  response ->
                      outcome.set(
                          response.getStatusCode() == HttpStatus.NOT_MODIFIED
                              ? "not_modified"
                              : "ok"))
              .doOnCancel(() -> outcome.set("cancelled"))
              .doFinally(
                  ignored -> {
                    String result = outcome.get();
                    Counter.builder("launchforge.edge.snapshot")
                        .tag("credential", credentialClass)
                        .tag("outcome", result)
                        .register(registry)
                        .increment();
                    sample.stop(
                        Timer.builder("launchforge.edge.snapshot.duration")
                            .tag("credential", credentialClass)
                            .tag("outcome", result)
                            .register(registry));
                  });
        });
  }
}
