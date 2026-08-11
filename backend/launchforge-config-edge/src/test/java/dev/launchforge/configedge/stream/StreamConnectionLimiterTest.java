package dev.launchforge.configedge.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.launchforge.configedge.configuration.ConfigEdgeProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StreamConnectionLimiterTest {
  @Test
  void enforcesPerKeyAndGlobalBoundsAndReleasesIdempotently() {
    StreamConnectionLimiter limiter =
        new StreamConnectionLimiter(
            new ConfigEdgeProperties(1024, Duration.ofSeconds(1), Duration.ofSeconds(1), 2, 1),
            new SimpleMeterRegistry());
    UUID firstKey = UUID.randomUUID();
    StreamConnectionLimiter.Lease first = limiter.acquire(firstKey);
    StreamConnectionLimiter.Lease second = limiter.acquire(UUID.randomUUID());

    assertEquals(2, limiter.activeConnections());
    assertThrows(ConnectionLimitExceededException.class, () -> limiter.acquire(firstKey));
    first.close();
    first.close();
    assertEquals(1, limiter.activeConnections());
    second.close();
    assertEquals(0, limiter.activeConnections());
  }
}
