package dev.launchforge.configedge.persistence;

import dev.launchforge.configedge.configuration.ConfigEdgeProperties;
import dev.launchforge.configedge.snapshot.SnapshotIntegrityVerifier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Primary
@Repository
@ConditionalOnProperty(
    prefix = "launchforge.config-edge",
    name = "redis-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RedisBackedEdgeRepository implements EdgeRepository {
  public static final String KEY_PREFIX = "launchforge:config:snapshot:";
  private static final DefaultRedisScript<Long> BACKFILL_SCRIPT = backfillScript();
  private final JdbcEdgeRepository database;
  private final StringRedisTemplate redis;
  private final SnapshotIntegrityVerifier snapshotVerifier;
  private final Semaphore databaseFallbacks;
  private final Duration fallbackAcquireTimeout;
  private final String invalidationChannel;
  private final Counter cacheHits;
  private final Counter cacheMisses;
  private final Counter cacheErrors;
  private final Counter databaseFallbackReads;
  private final Counter databaseFallbackRejected;

  public RedisBackedEdgeRepository(
      JdbcEdgeRepository database,
      StringRedisTemplate redis,
      SnapshotIntegrityVerifier snapshotVerifier,
      ConfigEdgeProperties properties,
      MeterRegistry registry) {
    this.database = database;
    this.redis = redis;
    this.snapshotVerifier = snapshotVerifier;
    this.databaseFallbacks = new Semaphore(properties.maximumConcurrentDatabaseFallbacks());
    this.fallbackAcquireTimeout = properties.databaseFallbackAcquireTimeout();
    this.invalidationChannel = properties.invalidationChannel();
    this.cacheHits = registry.counter("launchforge.edge.snapshot.cache", "outcome", "hit");
    this.cacheMisses = registry.counter("launchforge.edge.snapshot.cache", "outcome", "miss");
    this.cacheErrors = registry.counter("launchforge.edge.snapshot.cache", "outcome", "error");
    this.databaseFallbackReads =
        registry.counter("launchforge.edge.snapshot.fallback", "outcome", "read");
    this.databaseFallbackRejected =
        registry.counter("launchforge.edge.snapshot.fallback", "outcome", "rejected");
  }

  @Override
  public Optional<StoredSdkCredential> findCredential(String lookupId) {
    return database.findCredential(lookupId);
  }

  @Override
  public Optional<CredentialLifecycle> findLifecycle(UUID keyId) {
    return database.findLifecycle(keyId);
  }

  @Override
  public Optional<StoredSnapshot> findCurrentSnapshot(UUID environmentId) {
    try {
      Map<Object, Object> values = redis.opsForHash().entries(key(environmentId));
      if (!values.isEmpty()) {
        StoredSnapshot snapshot = storedSnapshot(environmentId, values);
        cacheHits.increment();
        return Optional.of(snapshot);
      }
      cacheMisses.increment();
    } catch (RuntimeException exception) {
      cacheErrors.increment();
    }
    Optional<StoredSnapshot> fallback =
        databaseFallback(() -> database.findCurrentSnapshot(environmentId), Optional.empty());
    fallback.ifPresent(
        snapshot -> {
          snapshotVerifier.verify(snapshot);
          backfillBestEffort(snapshot);
        });
    return fallback;
  }

  @Override
  public OptionalLong findCurrentRevision(UUID environmentId) {
    try {
      Object value = redis.opsForHash().get(key(environmentId), "revision");
      if (value != null) {
        long revision = positiveLong(value, "revision");
        cacheHits.increment();
        return OptionalLong.of(revision);
      }
      cacheMisses.increment();
    } catch (RuntimeException exception) {
      cacheErrors.increment();
    }
    return databaseFallback(
        () -> database.findCurrentRevision(environmentId), OptionalLong.empty());
  }

  @Override
  public void recordUse(UUID keyId) {
    database.recordUse(keyId);
  }

  @Override
  public Optional<EnvironmentScope> findEnvironmentScope(UUID environmentId) {
    return database.findEnvironmentScope(environmentId);
  }

  @Override
  public Optional<StoredBrowserCredential> findBrowserCredential(String clientKey) {
    return database.findBrowserCredential(clientKey);
  }

  @Override
  public Optional<BrowserCredentialLifecycle> findBrowserLifecycle(UUID keyId) {
    return database.findBrowserLifecycle(keyId);
  }

  @Override
  public void recordBrowserUse(UUID keyId) {
    database.recordBrowserUse(keyId);
  }

  private <T> T databaseFallback(Supplier<T> operation, T rejectedValue) {
    boolean acquired = false;
    try {
      acquired =
          databaseFallbacks.tryAcquire(fallbackAcquireTimeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!acquired) {
        databaseFallbackRejected.increment();
        return rejectedValue;
      }
      databaseFallbackReads.increment();
      return operation.get();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      databaseFallbackRejected.increment();
      return rejectedValue;
    } finally {
      if (acquired) {
        databaseFallbacks.release();
      }
    }
  }

  private void backfillBestEffort(StoredSnapshot snapshot) {
    try {
      redis.execute(
          BACKFILL_SCRIPT,
          List.of(key(snapshot.environmentId())),
          Long.toString(snapshot.revision()),
          Integer.toString(snapshot.schemaVersion()),
          snapshot.checksum(),
          snapshot.canonicalSnapshot(),
          invalidationChannel,
          revisionHint(snapshot.environmentId(), snapshot.revision()));
    } catch (RuntimeException exception) {
      cacheErrors.increment();
    }
  }

  private static StoredSnapshot storedSnapshot(UUID environmentId, Map<Object, Object> values) {
    long revision = positiveLong(required(values, "revision"), "revision");
    int schemaVersion =
        Math.toIntExact(positiveLong(required(values, "schemaVersion"), "schemaVersion"));
    String checksum = required(values, "checksum").toString();
    String snapshot = required(values, "snapshot").toString();
    return new StoredSnapshot(environmentId, revision, schemaVersion, snapshot, checksum);
  }

  private static Object required(Map<Object, Object> values, String field) {
    Object value = values.get(field);
    if (value == null) {
      throw new IllegalArgumentException("Redis snapshot is missing " + field);
    }
    return value;
  }

  private static long positiveLong(Object value, String field) {
    try {
      long parsed = Long.parseLong(value.toString());
      if (parsed < 1) {
        throw new NumberFormatException("non-positive");
      }
      return parsed;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Redis snapshot has invalid " + field, exception);
    }
  }

  private static String revisionHint(UUID environmentId, long revision) {
    return "{\"schemaVersion\":1,\"environmentId\":\""
        + environmentId
        + "\",\"revision\":"
        + revision
        + '}';
  }

  private static String key(UUID environmentId) {
    return KEY_PREFIX + environmentId;
  }

  private static DefaultRedisScript<Long> backfillScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setResultType(Long.class);
    script.setScriptText(
        """
        local current = redis.call('HGET', KEYS[1], 'revision')
        if current and tonumber(current) >= tonumber(ARGV[1]) then
          return 0
        end
        redis.call('HSET', KEYS[1],
          'revision', ARGV[1], 'schemaVersion', ARGV[2],
          'checksum', ARGV[3], 'snapshot', ARGV[4])
        redis.call('PUBLISH', ARGV[5], ARGV[6])
        return 1
        """);
    return script;
  }
}
