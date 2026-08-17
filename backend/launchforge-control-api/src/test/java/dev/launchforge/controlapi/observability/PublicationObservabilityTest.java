package dev.launchforge.controlapi.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

class PublicationObservabilityTest {
  @Test
  void publicationMetricsUseOnlyBoundedOperationAndOutcomeTags() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PublicationObservability observability =
        new PublicationObservability(registry, ObservationRegistry.create());

    assertEquals("published", observability.observe("publish", () -> "published"));
    assertThrows(
        IllegalStateException.class,
        () ->
            observability.observe(
                "rollback",
                () -> {
                  throw new IllegalStateException("expected");
                }));

    assertEquals(
        1.0,
        registry
            .get("launchforge.management.publication")
            .tags("operation", "publish", "outcome", "success")
            .counter()
            .count());
    assertEquals(
        1L,
        registry
            .get("launchforge.management.publication.duration")
            .tags("operation", "rollback", "outcome", "failure")
            .timer()
            .count());
  }
}
