package dev.launchforge.configedge.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class RevisionSignalBusTest {
  @Test
  void acceptsOnlyBoundedVersionedHintsForTheRequestedEnvironment() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    RevisionSignalBus bus = new RevisionSignalBus(new ObjectMapper(), registry);
    UUID environmentId = UUID.randomUUID();

    StepVerifier.create(bus.forEnvironment(environmentId).take(1))
        .then(
            () -> {
              bus.accept("{\"schemaVersion\":2,\"environmentId\":\"bad\",\"revision\":1}");
              bus.accept(
                  "{\"schemaVersion\":1,\"environmentId\":\""
                      + environmentId
                      + "\",\"revision\":7}");
            })
        .assertNext(hint -> assertEquals(7, hint.revision()))
        .verifyComplete();

    assertEquals(
        1.0,
        registry
            .get("launchforge.edge.revision.hint")
            .tag("outcome", "accepted")
            .counter()
            .count());
    assertEquals(
        1.0,
        registry
            .get("launchforge.edge.revision.hint")
            .tag("outcome", "rejected")
            .counter()
            .count());
  }
}
