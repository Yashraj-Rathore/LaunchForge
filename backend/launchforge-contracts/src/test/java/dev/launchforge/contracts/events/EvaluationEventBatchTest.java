package dev.launchforge.contracts.events;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvaluationEventBatchTest {
  @Test
  void boundsSchemaIdentifiersAndBatchSize() {
    EvaluationEvent event =
        new EvaluationEvent(
            UUID.randomUUID(),
            Instant.parse("2026-08-13T12:00:00Z"),
            "checkout",
            "on",
            "RULE_MATCH",
            4);

    assertThrows(
        IllegalArgumentException.class, () -> new EvaluationEventBatch("wrong", 1, List.of(event)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new EvaluationEvent(
                UUID.randomUUID(), Instant.now(), "Checkout Secret", "on", "RULE_MATCH", 4));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new EvaluationEventBatch(
                EvaluationEventBatch.EVENT_TYPE,
                EvaluationEventBatch.SCHEMA_VERSION,
                java.util.Collections.nCopies(101, event)));
  }
}
