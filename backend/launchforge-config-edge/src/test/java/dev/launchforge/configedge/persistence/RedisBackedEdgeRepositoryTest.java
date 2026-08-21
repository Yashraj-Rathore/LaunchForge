package dev.launchforge.configedge.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisBackedEdgeRepositoryTest {
  private final JdbcEdgeRepository database = mock(JdbcEdgeRepository.class);
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  private final SnapshotIntegrityVerifier snapshotVerifier = mock(SnapshotIntegrityVerifier.class);
  private final RedisMaterializationVerifier materializationVerifier =
      mock(RedisMaterializationVerifier.class);

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
            materializationVerifier,
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
  void servesAVerifiedRedisSnapshotWithoutUsingPostgres() {
    UUID environmentId = UUID.randomUUID();
    when(hashes.entries(RedisBackedEdgeRepository.KEY_PREFIX + environmentId))
        .thenReturn(
            Map.of(
                "revision", "3",
                "schemaVersion", "1",
                "checksum", "a".repeat(64),
                "snapshot", "{}",
                "provenanceVersion", "1",
                "provenanceKeyId", "test-v1",
                "snapshotSignature", "signature"));

    EdgeRepository.StoredSnapshot result =
        repository.findCurrentSnapshot(environmentId).orElseThrow();

    assertEquals(3, result.revision());
    verify(materializationVerifier)
        .verifySnapshot(environmentId, 3, 1, "a".repeat(64), "{}", "1", "test-v1", "signature");
    verify(database, never()).findCurrentSnapshot(any());
  }

  @Test
  void fallsBackToPostgresWithoutGrantingEdgeMaterializationWrites() {
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
  void rejectsAnUnverifiedDatabaseFallback() {
    UUID environmentId = UUID.randomUUID();
    EdgeRepository.StoredSnapshot stored =
        new EdgeRepository.StoredSnapshot(environmentId, 6, 1, "{}", "d".repeat(64));
    when(hashes.entries(RedisBackedEdgeRepository.KEY_PREFIX + environmentId)).thenReturn(Map.of());
    when(database.findCurrentSnapshot(environmentId)).thenReturn(Optional.of(stored));
    doThrow(new SnapshotUnavailableException()).when(snapshotVerifier).verify(stored);

    assertThrows(
        SnapshotUnavailableException.class, () -> repository.findCurrentSnapshot(environmentId));
  }

  @Test
  void forgedSelfConsistentRedisSnapshotIsNotServedWhenPostgresIsUnavailable() {
    UUID environmentId = UUID.randomUUID();
    Map<Object, Object> forged =
        Map.of(
            "revision", "7",
            "schemaVersion", "1",
            "checksum", "e".repeat(64),
            "snapshot", "{\"revision\":7}",
            "provenanceVersion", "1",
            "provenanceKeyId", "attacker",
            "snapshotSignature", "forged");
    when(hashes.entries(RedisBackedEdgeRepository.KEY_PREFIX + environmentId)).thenReturn(forged);
    doThrow(new IllegalArgumentException("untrusted signer"))
        .when(materializationVerifier)
        .verifySnapshot(
            any(), any(Long.class), any(Integer.class), any(), any(), any(), any(), any());
    when(database.findCurrentSnapshot(environmentId)).thenReturn(Optional.empty());

    assertTrue(repository.findCurrentSnapshot(environmentId).isEmpty());
    verify(database).findCurrentSnapshot(environmentId);
  }

  @Test
  void olderSignedSnapshotIsNotServedAfterANewerRevisionWasObserved() {
    UUID environmentId = UUID.randomUUID();
    AtomicInteger reads = new AtomicInteger();
    when(hashes.entries(RedisBackedEdgeRepository.KEY_PREFIX + environmentId))
        .thenAnswer(ignored -> redisSnapshot(reads.getAndIncrement() == 0 ? 9 : 8));
    when(database.findCurrentSnapshot(environmentId)).thenReturn(Optional.empty());

    assertEquals(9, repository.findCurrentSnapshot(environmentId).orElseThrow().revision());
    assertTrue(repository.findCurrentSnapshot(environmentId).isEmpty());
  }

  @Test
  void currentRevisionRequiresValidProvenance() {
    UUID environmentId = UUID.randomUUID();
    when(hashes.multiGet(
            RedisBackedEdgeRepository.KEY_PREFIX + environmentId,
            List.of("revision", "provenanceVersion", "provenanceKeyId", "revisionSignature")))
        .thenReturn(List.of("11", "1", "test-v1", "revision-signature"));

    OptionalLong revision = repository.findCurrentRevision(environmentId);

    assertEquals(11, revision.orElseThrow());
    verify(materializationVerifier)
        .verifyRevision(environmentId, 11, "1", "test-v1", "revision-signature");
    verify(database, never()).findCurrentRevision(any());
  }

  private static Map<Object, Object> redisSnapshot(long revision) {
    return Map.of(
        "revision", Long.toString(revision),
        "schemaVersion", "1",
        "checksum", "f".repeat(64),
        "snapshot", "{\"revision\":" + revision + '}',
        "provenanceVersion", "1",
        "provenanceKeyId", "test-v1",
        "snapshotSignature", "signature");
  }
}
