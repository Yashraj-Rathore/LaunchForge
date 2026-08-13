package dev.launchforge.contracts.events;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfigRevisionPublishedEventTest {
  @Test
  void rejectsUnsupportedSchemaAndInvalidChecksum() {
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ConfigRevisionPublishedEvent(
                id,
                ConfigRevisionPublishedEvent.EVENT_TYPE,
                2,
                now,
                id,
                id,
                id,
                1,
                "0".repeat(64),
                id.toString()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ConfigRevisionPublishedEvent(
                id,
                ConfigRevisionPublishedEvent.EVENT_TYPE,
                1,
                now,
                id,
                id,
                id,
                1,
                "secret-value",
                id.toString()));
  }
}
