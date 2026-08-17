package dev.launchforge.configedge.stream;

import dev.launchforge.configedge.configuration.ConfigEdgeProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class StreamConnectionLimiter {
  private final int maximumConnections;
  private final int maximumConnectionsPerKey;
  private final StreamConnectionStore distributedStore;
  private final AtomicInteger active = new AtomicInteger();
  private final Map<UUID, Integer> activeByKey = new HashMap<>();
  private final io.micrometer.core.instrument.Counter distributedRejected;
  private final io.micrometer.core.instrument.Counter distributedFallback;
  private final io.micrometer.core.instrument.Counter opened;
  private final io.micrometer.core.instrument.Counter closed;
  private final io.micrometer.core.instrument.Counter rejected;

  @Autowired
  public StreamConnectionLimiter(
      ConfigEdgeProperties properties,
      RedisStreamConnectionStore distributedStore,
      MeterRegistry registry) {
    this(properties, (StreamConnectionStore) distributedStore, registry);
  }

  StreamConnectionLimiter(ConfigEdgeProperties properties, MeterRegistry registry) {
    this(properties, unavailableStore(), registry);
  }

  StreamConnectionLimiter(
      ConfigEdgeProperties properties,
      StreamConnectionStore distributedStore,
      MeterRegistry registry) {
    maximumConnections = properties.maximumConnections();
    maximumConnectionsPerKey = properties.maximumConnectionsPerKey();
    this.distributedStore = distributedStore;
    Gauge.builder("launchforge.edge.stream.connections.active", active, AtomicInteger::get)
        .description("Active authenticated Config Edge SSE connections")
        .register(registry);
    distributedRejected =
        registry.counter(
            "launchforge.security.stream.connection", "outcome", "distributed_rejected");
    distributedFallback =
        registry.counter("launchforge.security.stream.connection", "outcome", "local_fallback");
    opened = registry.counter("launchforge.edge.stream.connection", "outcome", "opened");
    closed = registry.counter("launchforge.edge.stream.connection", "outcome", "closed");
    rejected = registry.counter("launchforge.edge.stream.connection", "outcome", "rejected");
  }

  public synchronized Lease acquire(UUID keyId) {
    int keyConnections = activeByKey.getOrDefault(keyId, 0);
    if (active.get() >= maximumConnections || keyConnections >= maximumConnectionsPerKey) {
      rejected.increment();
      throw new ConnectionLimitExceededException();
    }
    active.incrementAndGet();
    activeByKey.put(keyId, keyConnections + 1);
    UUID connectionId = UUID.randomUUID();
    StreamConnectionStore.Result result = distributedStore.acquire(connectionId, keyId);
    if (result == StreamConnectionStore.Result.REJECTED) {
      distributedRejected.increment();
      rejected.increment();
      releaseLocal(keyId);
      throw new ConnectionLimitExceededException();
    }
    if (result == StreamConnectionStore.Result.UNAVAILABLE) {
      distributedFallback.increment();
    }
    opened.increment();
    return new Lease(this, connectionId, keyId);
  }

  public int activeConnections() {
    return active.get();
  }

  private void renew(UUID connectionId, UUID keyId) {
    StreamConnectionStore.Result result = distributedStore.renew(connectionId, keyId);
    if (result == StreamConnectionStore.Result.REJECTED) {
      throw new ConnectionLimitExceededException();
    }
    if (result == StreamConnectionStore.Result.UNAVAILABLE) {
      distributedFallback.increment();
    }
  }

  private void release(UUID connectionId, UUID keyId) {
    distributedStore.release(connectionId, keyId);
    releaseLocal(keyId);
    closed.increment();
  }

  private synchronized void releaseLocal(UUID keyId) {
    Integer keyConnections = activeByKey.get(keyId);
    if (keyConnections == null) {
      return;
    }
    if (keyConnections == 1) {
      activeByKey.remove(keyId);
    } else {
      activeByKey.put(keyId, keyConnections - 1);
    }
    active.decrementAndGet();
  }

  public static final class Lease implements AutoCloseable {
    private final StreamConnectionLimiter owner;
    private final UUID connectionId;
    private final UUID keyId;
    private final AtomicBoolean closed = new AtomicBoolean();

    private Lease(StreamConnectionLimiter owner, UUID connectionId, UUID keyId) {
      this.owner = owner;
      this.connectionId = connectionId;
      this.keyId = keyId;
    }

    public void renew() {
      if (!closed.get()) {
        owner.renew(connectionId, keyId);
      }
    }

    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        owner.release(connectionId, keyId);
      }
    }
  }

  private static StreamConnectionStore unavailableStore() {
    return new StreamConnectionStore() {
      @Override
      public Result acquire(UUID connectionId, UUID keyId) {
        return Result.UNAVAILABLE;
      }

      @Override
      public Result renew(UUID connectionId, UUID keyId) {
        return Result.UNAVAILABLE;
      }

      @Override
      public void release(UUID connectionId, UUID keyId) {}
    };
  }
}
