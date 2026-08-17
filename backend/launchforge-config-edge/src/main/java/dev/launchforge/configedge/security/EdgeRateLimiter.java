package dev.launchforge.configedge.security;

import dev.launchforge.configedge.configuration.EdgeAbuseProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public final class EdgeRateLimiter {
  private static final String KEY_PREFIX = "launchforge:security:rate:v1:edge:";
  private static final int MAXIMUM_LOCAL_PARTITIONS = 100_000;
  private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = incrementScript();

  private final StringRedisTemplate redis;
  private final EdgeAbuseProperties properties;
  private final Clock clock;
  private final ConcurrentHashMap<String, Window> localWindows = new ConcurrentHashMap<>();
  private final Counter allowed;
  private final Counter rejected;
  private final Counter fallback;

  public EdgeRateLimiter(
      StringRedisTemplate redis,
      EdgeAbuseProperties properties,
      Clock clock,
      MeterRegistry registry) {
    this.redis = redis;
    this.properties = properties;
    this.clock = clock;
    this.allowed =
        registry.counter("launchforge.security.rate.limit", "plane", "edge", "outcome", "allowed");
    this.rejected =
        registry.counter("launchforge.security.rate.limit", "plane", "edge", "outcome", "rejected");
    this.fallback =
        registry.counter("launchforge.security.rate.limit", "plane", "edge", "outcome", "fallback");
  }

  public Decision check(Policy policy, UUID keyId) {
    long now = clock.instant().getEpochSecond();
    long windowSeconds = properties.window().toSeconds();
    long window = now / windowSeconds;
    long count = -1;
    if (properties.redisEnabled()) {
      try {
        Long result =
            redis.execute(
                INCREMENT_SCRIPT,
                List.of(redisKey(policy, keyId, window)),
                Long.toString(properties.window().plusSeconds(1).toMillis()));
        count = result == null ? -1 : result;
      } catch (RuntimeException exception) {
        fallback.increment();
      }
    }
    if (count < 0) {
      count = localIncrement(policy, keyId, window);
    }
    boolean permitted = count <= limit(policy);
    (permitted ? allowed : rejected).increment();
    long retryAfter = Math.max(1, ((window + 1) * windowSeconds) - now);
    return new Decision(permitted, retryAfter);
  }

  private long localIncrement(Policy policy, UUID keyId, long window) {
    String key = policy.name() + ':' + keyId;
    Window current =
        localWindows.compute(
            key,
            (ignored, existing) ->
                existing == null || existing.window() != window
                    ? new Window(window, new AtomicInteger())
                    : existing);
    if (localWindows.size() > MAXIMUM_LOCAL_PARTITIONS) {
      localWindows.entrySet().removeIf(entry -> entry.getValue().window() < window - 1);
    }
    return current.count().incrementAndGet();
  }

  private int limit(Policy policy) {
    return switch (policy) {
      case SNAPSHOT -> properties.snapshotRequestsPerWindow();
      case STREAM -> properties.streamStartsPerWindow();
      case ANALYTICS -> properties.analyticsRequestsPerWindow();
    };
  }

  private static String redisKey(Policy policy, UUID keyId, long window) {
    return KEY_PREFIX + policy.name().toLowerCase(Locale.ROOT) + ':' + keyId + ':' + window;
  }

  private static DefaultRedisScript<Long> incrementScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setResultType(Long.class);
    script.setScriptText(
        """
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then
          redis.call('PEXPIRE', KEYS[1], ARGV[1])
        end
        return count
        """);
    return script;
  }

  public enum Policy {
    SNAPSHOT,
    STREAM,
    ANALYTICS
  }

  public record Decision(boolean permitted, long retryAfterSeconds) {}

  private record Window(long window, AtomicInteger count) {}
}
