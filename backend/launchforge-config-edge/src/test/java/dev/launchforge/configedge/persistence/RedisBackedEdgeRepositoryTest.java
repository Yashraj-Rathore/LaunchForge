package dev.launchforge.configedge.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.launchforge.configedge.configuration.ConfigEdgeProperties;
import dev.launchforge.configedge.snapshot.SnapshotIntegrityVerifier;
import dev.launchforge.configedge.snapshot.SnapshotUnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisBackedEdgeRepositoryTest {
  private final JdbcEdgeRepository database = mock(JdbcEdgeRepository.class);
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  private final SnapshotIntegrityVerifier snapshotVerifier = mock(SnapshotIntegrityVerifier.class);

  @SuppressWarnings("unchecked")
  private final HashOperations<String, Object, Object> hashes =
      (HashOperations<String, Object, Object>) mock(HashOperations.class);

  private RedisBackedEdgeRepository repository;

  @BeforeEach
  void setUp() {
    when(redis.opsForHash()).thenReturn(hashes);
    repository =
        new RedisBackedEdgeRepository(
            database,
            redis,
            snapshotVerifier,
            new ConfigEdgeProperties(
                1024,
                Duration.ofSeconds(1),
                Duration.ofSeconds(15),
                10,
                2,
                1,
                Duration.ofMillis(10),
                "launchforge:config:revision-hints:v1"),
            new SimpleMeterRegistry());
  }

  @Test
  void servesACompleteRedisSnapshotWithoutUsingPostgres() {
    UUID environmentId = UUID.randomUUID();
    when(hashes.entries(RedisBackedEdgeRepository.KEY_PREFIX + environmentId))
        .thenReturn(
            Map.of(
                "revision", "3",
                "schemaVersion", "1",
                "checksum", "a".repeat(64),
                "snapshot", "{}"));

    EdgeRepository.StoredSnapshot result =
        repository.findCurrentSnapshot(environmentId).orElseThrow();

    assertEquals(3, result.revision());
    verify(database, never()).findCurrentSnapshot(any());
  }

  @Test
  void fallsBackToPostgresAndBackfillsWhenRedisIsEmpty() {
    UUID environmentId = UUID.randomUUID();
    EdgeRepository.StoredSnapshot stored =
        new EdgeRepository.StoredSnapshot(environmentId, 4, 1, "{}", "b".repeat(64));
    when(hashes.entries(RedisBackedEdgeRepository.KEY_PREFIX + environmentId)).thenReturn(Map.of());
    when(database.findCurrentSnapshot(environmentId)).thenReturn(Optional.of(stored));
    assertEquals(4, repository.findCurrentSnapshot(environmentId).orElseThrow().revision());
    verify(database).findCurrentSnapshot(environmentId);
    verify(snapshotVerifier).verify(stored);
  }

  @Test
  void treatsRedisFailureAsACacheMiss() {
    UUID environmentId = UUID.randomUUID();
    EdgeRepository.StoredSnapshot stored =
        new EdgeRepository.StoredSnapshot(environmentId, 5, 1, "{}", "c".repeat(64));
    when(hashes.entries(RedisBackedEdgeRepository.KEY_PREFIX + environmentId))
        .thenThrow(new IllegalStateException("redis unavailable"));
    when(database.findCurrentSnapshot(environmentId)).thenReturn(Optional.of(stored));

    assertEquals(5, repository.findCurrentSnapshot(environmentId).orElseThrow().revision());
  }

  @Test
  void rejectsAnUnverifiedDatabaseFallbackBeforeBackfill() {
    UUID environmentId = UUID.randomUUID();
    EdgeRepository.StoredSnapshot stored =
        new EdgeRepository.StoredSnapshot(environmentId, 6, 1, "{}", "d".repeat(64));
    when(hashes.entries(RedisBackedEdgeRepository.KEY_PREFIX + environmentId)).thenReturn(Map.of());
    when(database.findCurrentSnapshot(environmentId)).thenReturn(Optional.of(stored));
    doThrow(new SnapshotUnavailableException()).when(snapshotVerifier).verify(stored);

    assertThrows(
        SnapshotUnavailableException.class, () -> repository.findCurrentSnapshot(environmentId));
  }
}
