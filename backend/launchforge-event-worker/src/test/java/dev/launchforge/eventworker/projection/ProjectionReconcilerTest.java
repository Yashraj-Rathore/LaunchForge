package dev.launchforge.eventworker.projection;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.launchforge.eventworker.configuration.DistributionProperties;
import dev.launchforge.eventworker.observability.DistributionMetrics;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectionReconcilerTest {
  @Test
  void isolatesPoisonSnapshotAdvancesCursorAndRetriesItOnTheNextCompleteScan() {
    AuthoritativeSnapshotRepository repository = mock(AuthoritativeSnapshotRepository.class);
    SnapshotValidator validator = mock(SnapshotValidator.class);
    RedisSnapshotMaterializer materializer = mock(RedisSnapshotMaterializer.class);
    DistributionProperties properties = mock(DistributionProperties.class);
    DistributionMetrics metrics = mock(DistributionMetrics.class);
    AuthoritativeSnapshot poison = snapshot("00000000-0000-0000-0000-000000000001");
    AuthoritativeSnapshot firstHealthy = snapshot("00000000-0000-0000-0000-000000000002");
    AuthoritativeSnapshot nextHealthy = snapshot("00000000-0000-0000-0000-000000000003");
    when(properties.reconciliationBatchSize()).thenReturn(2);
    when(repository.findCurrentPage(null, 2)).thenReturn(List.of(poison, firstHealthy));
    when(repository.findCurrentPage(firstHealthy.environmentId(), 2))
        .thenReturn(List.of(nextHealthy));
    doThrow(new IllegalArgumentException("Stored snapshot integrity check failed"))
        .when(validator)
        .validate(poison);
    when(materializer.materialize(firstHealthy)).thenReturn(true);
    when(materializer.materialize(nextHealthy)).thenReturn(true);
    ProjectionReconciler reconciler =
        new ProjectionReconciler(repository, validator, materializer, properties, metrics);

    reconciler.reconcile();
    reconciler.reconcile();
    reconciler.reconcile();

    verify(metrics, org.mockito.Mockito.times(2)).projectionError();
    verify(metrics, org.mockito.Mockito.times(3)).projectionAdvanced();
    verify(validator, org.mockito.Mockito.times(2)).validate(poison);
    verify(materializer, org.mockito.Mockito.never()).materialize(poison);
    verify(materializer, org.mockito.Mockito.times(2)).materialize(firstHealthy);
    verify(materializer).materialize(nextHealthy);
    verify(repository).findCurrentPage(firstHealthy.environmentId(), 2);
  }

  private static AuthoritativeSnapshot snapshot(String environmentId) {
    return new AuthoritativeSnapshot(
        UUID.fromString("10000000-0000-0000-0000-000000000001"),
        UUID.fromString("20000000-0000-0000-0000-000000000001"),
        UUID.fromString(environmentId),
        1,
        1,
        1,
        "{}",
        "0".repeat(64));
  }
}
