package dev.launchforge.configedge.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @Test
  void distributedRejectionRollsBackLocalLeaseAndRenewalsReachTheSharedStore() {
    FakeStore store = new FakeStore();
    StreamConnectionLimiter limiter =
        new StreamConnectionLimiter(
            new ConfigEdgeProperties(1024, Duration.ofSeconds(1), Duration.ofSeconds(1), 2, 2),
            store,
            new SimpleMeterRegistry());
    UUID key = UUID.randomUUID();
    store.result = StreamConnectionStore.Result.REJECTED;

    assertThrows(ConnectionLimitExceededException.class, () -> limiter.acquire(key));
    assertEquals(0, limiter.activeConnections());

    store.result = StreamConnectionStore.Result.ACQUIRED;
    StreamConnectionLimiter.Lease lease = limiter.acquire(key);
    lease.renew();
    lease.close();
    assertTrue(store.renewed);
    assertTrue(store.released);
  }

  private static final class FakeStore implements StreamConnectionStore {
    private Result result = Result.ACQUIRED;
    private boolean renewed;
    private boolean released;

    @Override
    public Result acquire(UUID connectionId, UUID keyId) {
      return result;
    }

    @Override
    public Result renew(UUID connectionId, UUID keyId) {
      renewed = true;
      return result;
    }

    @Override
    public void release(UUID connectionId, UUID keyId) {
      released = true;
    }
  }
}
