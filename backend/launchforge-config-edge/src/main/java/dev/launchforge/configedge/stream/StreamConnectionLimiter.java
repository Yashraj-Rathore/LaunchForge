package dev.launchforge.configedge.stream;

import dev.launchforge.configedge.configuration.ConfigEdgeProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public final class StreamConnectionLimiter {
  private final int maximumConnections;
  private final int maximumConnectionsPerKey;
  private final AtomicInteger active = new AtomicInteger();
  private final Map<UUID, Integer> activeByKey = new HashMap<>();

  public StreamConnectionLimiter(ConfigEdgeProperties properties, MeterRegistry registry) {
    maximumConnections = properties.maximumConnections();
    maximumConnectionsPerKey = properties.maximumConnectionsPerKey();
    Gauge.builder("launchforge.edge.stream.connections.active", active, AtomicInteger::get)
        .description("Active authenticated Config Edge SSE connections")
        .register(registry);
  }

  public synchronized Lease acquire(UUID keyId) {
    int keyConnections = activeByKey.getOrDefault(keyId, 0);
    if (active.get() >= maximumConnections || keyConnections >= maximumConnectionsPerKey) {
      throw new ConnectionLimitExceededException();
    }
    active.incrementAndGet();
    activeByKey.put(keyId, keyConnections + 1);
    return new Lease(this, keyId);
  }

  public int activeConnections() {
    return active.get();
  }

  private synchronized void release(UUID keyId) {
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
    private final UUID keyId;
    private final AtomicBoolean closed = new AtomicBoolean();

    private Lease(StreamConnectionLimiter owner, UUID keyId) {
      this.owner = owner;
      this.keyId = keyId;
    }

    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        owner.release(keyId);
      }
    }
  }
}
