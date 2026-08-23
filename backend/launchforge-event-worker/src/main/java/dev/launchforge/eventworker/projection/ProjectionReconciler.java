package dev.launchforge.eventworker.projection;

import dev.launchforge.eventworker.configuration.DistributionProperties;
import dev.launchforge.eventworker.configuration.WorkerSchedulingConfiguration;
import dev.launchforge.eventworker.observability.DistributionMetrics;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProjectionReconciler {
  private final AuthoritativeSnapshotRepository repository;
  private final SnapshotValidator validator;
  private final RedisSnapshotMaterializer materializer;
  private final DistributionMetrics metrics;
  private final int batchSize;
  private UUID cursor;

  public ProjectionReconciler(
      AuthoritativeSnapshotRepository repository,
      SnapshotValidator validator,
      RedisSnapshotMaterializer materializer,
      DistributionProperties properties,
      DistributionMetrics metrics) {
    this.repository = repository;
    this.validator = validator;
    this.materializer = materializer;
    this.batchSize = properties.reconciliationBatchSize();
    this.metrics = metrics;
  }

  @Scheduled(
      fixedDelayString = "${launchforge.distribution.reconciliation-interval:5s}",
      scheduler = WorkerSchedulingConfiguration.CONFIGURATION_SCHEDULER)
  public void reconcile() {
    List<AuthoritativeSnapshot> page = repository.findCurrentPage(cursor, batchSize);
    if (page.isEmpty()) {
      cursor = null;
      return;
    }
    for (AuthoritativeSnapshot snapshot : page) {
      long started = System.nanoTime();
      String outcome = "error";
      try {
        validator.validate(snapshot);
        if (materializer.materialize(snapshot)) {
          outcome = "advanced";
          metrics.projectionAdvanced();
        } else {
          outcome = "ignored";
          metrics.projectionIgnored();
        }
      } catch (RuntimeException exception) {
        metrics.projectionError();
        throw exception;
      } finally {
        metrics.recordProjectionDuration("reconcile", outcome, System.nanoTime() - started);
      }
    }
    cursor = page.getLast().environmentId();
    if (page.size() < batchSize) {
      cursor = null;
    }
  }
}
