package dev.launchforge.eventworker.projection;

import dev.launchforge.eventworker.configuration.DistributionProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisSnapshotMaterializer {
  public static final String KEY_PREFIX = "launchforge:config:snapshot:";
  private static final DefaultRedisScript<Long> APPLY_SCRIPT = script();
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final String invalidationChannel;

  public RedisSnapshotMaterializer(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      DistributionProperties properties) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.invalidationChannel = properties.invalidationChannel();
  }

  public boolean materialize(AuthoritativeSnapshot snapshot) {
    String hint = revisionHint(snapshot.environmentId(), snapshot.revision());
    Long result =
        redisTemplate.execute(
            APPLY_SCRIPT,
            List.of(key(snapshot.environmentId())),
            Long.toString(snapshot.revision()),
            Integer.toString(snapshot.schemaVersion()),
            snapshot.checksum(),
            snapshot.canonicalSnapshot(),
            invalidationChannel,
            hint);
    if (result == null) {
      throw new IllegalStateException("Redis did not return a materialization result");
    }
    return result == 1L;
  }

  public static String key(UUID environmentId) {
    return KEY_PREFIX + environmentId;
  }

  private String revisionHint(UUID environmentId, long revision) {
    try {
      return objectMapper.writeValueAsString(
          new RevisionHint(1, environmentId.toString(), revision));
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to encode bounded revision hint", exception);
    }
  }

  private static DefaultRedisScript<Long> script() {
    String lua =
        """
        local current = redis.call('HGET', KEYS[1], 'revision')
        if current and tonumber(current) >= tonumber(ARGV[1]) then
          return 0
        end
        redis.call('HSET', KEYS[1],
          'revision', ARGV[1],
          'schemaVersion', ARGV[2],
          'checksum', ARGV[3],
          'snapshot', ARGV[4])
        redis.call('PUBLISH', ARGV[5], ARGV[6])
        return 1
        """;
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText(lua);
    script.setResultType(Long.class);
    return script;
  }

  private record RevisionHint(int schemaVersion, String environmentId, long revision) {}
}
