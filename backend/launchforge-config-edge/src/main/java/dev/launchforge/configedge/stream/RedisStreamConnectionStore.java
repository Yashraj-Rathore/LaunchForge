package dev.launchforge.configedge.stream;

import dev.launchforge.configedge.configuration.ConfigEdgeProperties;
import dev.launchforge.configedge.configuration.EdgeAbuseProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
final class RedisStreamConnectionStore implements StreamConnectionStore {
  private static final String GLOBAL_KEY = "launchforge:security:{sse}:connections";
  private static final String KEY_PREFIX = "launchforge:security:{sse}:key:";
  private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = acquireScript();
  private static final DefaultRedisScript<Long> RENEW_SCRIPT = renewScript();
  private static final DefaultRedisScript<Long> RELEASE_SCRIPT = releaseScript();

  private final StringRedisTemplate redis;
  private final boolean enabled;
  private final int maximumConnections;
  private final int maximumConnectionsPerKey;
  private final Duration leaseDuration;
  private final Clock clock;

  RedisStreamConnectionStore(
      StringRedisTemplate redis,
      EdgeAbuseProperties abuseProperties,
      ConfigEdgeProperties edgeProperties,
      Clock clock) {
    this.redis = redis;
    this.enabled = abuseProperties.redisEnabled();
    this.maximumConnections = edgeProperties.maximumConnections();
    this.maximumConnectionsPerKey = edgeProperties.maximumConnectionsPerKey();
    this.leaseDuration = abuseProperties.streamLeaseDuration();
    this.clock = clock;
    if (leaseDuration.compareTo(edgeProperties.revisionPollInterval()) <= 0) {
      throw new IllegalArgumentException(
          "streamLeaseDuration must exceed the lifecycle revalidation interval");
    }
  }

  @Override
  public Result acquire(UUID connectionId, UUID keyId) {
    if (!enabled) {
      return Result.UNAVAILABLE;
    }
    long now = clock.millis();
    try {
      Long result =
          redis.execute(
              ACQUIRE_SCRIPT,
              List.of(GLOBAL_KEY, key(keyId)),
              Long.toString(now),
              Long.toString(now + leaseDuration.toMillis()),
              connectionId.toString(),
              Integer.toString(maximumConnections),
              Integer.toString(maximumConnectionsPerKey),
              Long.toString(leaseDuration.multipliedBy(2).toMillis()));
      return result != null && result == 1 ? Result.ACQUIRED : Result.REJECTED;
    } catch (RuntimeException exception) {
      return Result.UNAVAILABLE;
    }
  }

  @Override
  public Result renew(UUID connectionId, UUID keyId) {
    if (!enabled) {
      return Result.UNAVAILABLE;
    }
    long now = clock.millis();
    try {
      Long result =
          redis.execute(
              RENEW_SCRIPT,
              List.of(GLOBAL_KEY, key(keyId)),
              Long.toString(now + leaseDuration.toMillis()),
              connectionId.toString(),
              Long.toString(leaseDuration.multipliedBy(2).toMillis()));
      return result != null && result == 1 ? Result.ACQUIRED : Result.REJECTED;
    } catch (RuntimeException exception) {
      return Result.UNAVAILABLE;
    }
  }

  @Override
  public void release(UUID connectionId, UUID keyId) {
    if (!enabled) {
      return;
    }
    try {
      redis.execute(RELEASE_SCRIPT, List.of(GLOBAL_KEY, key(keyId)), connectionId.toString());
    } catch (RuntimeException exception) {
      // The lease expires automatically; local accounting is still released by the caller.
    }
  }

  private static String key(UUID keyId) {
    return KEY_PREFIX + keyId;
  }

  private static DefaultRedisScript<Long> acquireScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setResultType(Long.class);
    script.setScriptText(
        """
        redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
        redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[1])
        if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[4]) then return 0 end
        if redis.call('ZCARD', KEYS[2]) >= tonumber(ARGV[5]) then return 0 end
        redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3])
        redis.call('ZADD', KEYS[2], ARGV[2], ARGV[3])
        redis.call('PEXPIRE', KEYS[1], ARGV[6])
        redis.call('PEXPIRE', KEYS[2], ARGV[6])
        return 1
        """);
    return script;
  }

  private static DefaultRedisScript<Long> renewScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setResultType(Long.class);
    script.setScriptText(
        """
        if not redis.call('ZSCORE', KEYS[1], ARGV[2]) then return 0 end
        if not redis.call('ZSCORE', KEYS[2], ARGV[2]) then return 0 end
        redis.call('ZADD', KEYS[1], 'XX', ARGV[1], ARGV[2])
        redis.call('ZADD', KEYS[2], 'XX', ARGV[1], ARGV[2])
        redis.call('PEXPIRE', KEYS[1], ARGV[3])
        redis.call('PEXPIRE', KEYS[2], ARGV[3])
        return 1
        """);
    return script;
  }

  private static DefaultRedisScript<Long> releaseScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setResultType(Long.class);
    script.setScriptText(
        """
        redis.call('ZREM', KEYS[1], ARGV[1])
        redis.call('ZREM', KEYS[2], ARGV[1])
        return 1
        """);
    return script;
  }
}
