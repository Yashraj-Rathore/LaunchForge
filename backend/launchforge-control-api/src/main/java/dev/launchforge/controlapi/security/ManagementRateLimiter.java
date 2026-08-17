package dev.launchforge.controlapi.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public final class ManagementRateLimiter {
  private static final String KEY_PREFIX = "launchforge:security:rate:v1:control:";
  private static final int MAXIMUM_LOCAL_PARTITIONS = 100_000;
  private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = incrementScript();

  private final StringRedisTemplate redis;
  private final ControlPlaneAbuseProperties properties;
  private final Clock clock;
  private final ConcurrentHashMap<String, Window> localWindows = new ConcurrentHashMap<>();
  private final Counter allowed;
  private final Counter rejected;
  private final Counter fallback;

  @Autowired
  public ManagementRateLimiter(
      ObjectProvider<StringRedisTemplate> redisProvider,
      ControlPlaneAbuseProperties properties,
      Clock clock,
      MeterRegistry registry) {
    this(redisProvider.getIfAvailable(), properties, clock, registry);
  }

  ManagementRateLimiter(
      StringRedisTemplate redis,
      ControlPlaneAbuseProperties properties,
      Clock clock,
      MeterRegistry registry) {
    this.redis = redis;
    this.properties = properties;
    this.clock = clock;
    this.allowed =
        registry.counter(
            "launchforge.security.rate.limit", "plane", "control", "outcome", "allowed");
    this.rejected =
        registry.counter(
            "launchforge.security.rate.limit", "plane", "control", "outcome", "rejected");
    this.fallback =
        registry.counter(
            "launchforge.security.rate.limit", "plane", "control", "outcome", "fallback");
  }

  public Decision check(Policy policy, String partitionMaterial) {
    long now = clock.instant().getEpochSecond();
    long windowSeconds = properties.window().toSeconds();
    long window = now / windowSeconds;
    String partition = sha256(partitionMaterial);
    long count = -1;
    if (properties.redisEnabled() && redis != null) {
      try {
        Long result =
            redis.execute(
                INCREMENT_SCRIPT,
                List.of(redisKey(policy, partition, window)),
                Long.toString(properties.window().plusSeconds(1).toMillis()));
        count = result == null ? -1 : result;
      } catch (RuntimeException exception) {
        fallback.increment();
      }
    }
    if (count < 0) {
      count = localIncrement(policy, partition, window);
    }
    boolean permitted = count <= properties.limit(policy);
    (permitted ? allowed : rejected).increment();
    long retryAfter = Math.max(1, ((window + 1) * windowSeconds) - now);
    return new Decision(permitted, retryAfter);
  }

  private long localIncrement(Policy policy, String partition, long window) {
    String key = policy.name() + ':' + partition;
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

  private static String redisKey(Policy policy, String partition, long window) {
    return KEY_PREFIX + policy.name().toLowerCase(Locale.ROOT) + ':' + partition + ':' + window;
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
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
    LOGIN,
    MANAGEMENT_READ,
    MANAGEMENT_MUTATION,
    KEY_LIFECYCLE
  }

  public record Decision(boolean permitted, long retryAfterSeconds) {}

  private record Window(long window, AtomicInteger count) {}
}
