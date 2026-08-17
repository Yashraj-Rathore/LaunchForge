package dev.launchforge.configedge.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

class SnapshotMetricsTest {
  @Test
  void snapshotMetricsHaveBoundedCredentialAndOutcomeTags() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    SnapshotMetrics metrics = new SnapshotMetrics(registry);

    metrics.observe("server", Mono.just(ResponseEntity.ok("snapshot"))).block();
    metrics.observe("browser", Mono.just(ResponseEntity.status(304).build())).block();
    assertThrows(
        IllegalStateException.class,
        () -> metrics.observe("server", Mono.error(new IllegalStateException("expected"))).block());

    assertEquals(
        1.0,
        registry
            .get("launchforge.edge.snapshot")
            .tags("credential", "server", "outcome", "ok")
            .counter()
            .count());
    assertEquals(
        1L,
        registry
            .get("launchforge.edge.snapshot.duration")
            .tags("credential", "browser", "outcome", "not_modified")
            .timer()
            .count());
    assertEquals(
        1.0,
        registry
            .get("launchforge.edge.snapshot")
            .tags("credential", "server", "outcome", "error")
            .counter()
            .count());
  }
}
