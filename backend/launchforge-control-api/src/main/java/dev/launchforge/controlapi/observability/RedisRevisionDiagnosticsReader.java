package dev.launchforge.controlapi.observability;

import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public final class RedisRevisionDiagnosticsReader {
  private static final String KEY_PREFIX = "launchforge:config:snapshot:";
  private final StringRedisTemplate redis;

  public RedisRevisionDiagnosticsReader(ObjectProvider<StringRedisTemplate> redisProvider) {
    redis = redisProvider.getIfAvailable();
  }

  public OptionalLong revision(UUID environmentId) {
    if (redis == null) {
      return OptionalLong.empty();
    }
    try {
      String value = (String) redis.opsForHash().get(KEY_PREFIX + environmentId, "revision");
      if (value == null) {
        return OptionalLong.empty();
      }
      long revision = Long.parseLong(value);
      return revision >= 0 ? OptionalLong.of(revision) : OptionalLong.empty();
    } catch (DataAccessException | NumberFormatException exception) {
      return OptionalLong.empty();
    }
  }
}
