package dev.launchforge.eventworker.projection;

import dev.launchforge.contracts.events.ConfigRevisionPublishedEvent;
import dev.launchforge.eventworker.observability.DistributionMetrics;
import dev.launchforge.eventworker.outbox.ConfigRevisionEventCodec;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ConfigRevisionProjector {
  private final ConfigRevisionEventCodec codec;
  private final AuthoritativeSnapshotRepository repository;
  private final SnapshotValidator validator;
  private final RedisSnapshotMaterializer materializer;
  private final DistributionMetrics metrics;

  public ConfigRevisionProjector(
      ConfigRevisionEventCodec codec,
      AuthoritativeSnapshotRepository repository,
      SnapshotValidator validator,
      RedisSnapshotMaterializer materializer,
      DistributionMetrics metrics) {
    this.codec = codec;
    this.repository = repository;
    this.validator = validator;
    this.materializer = materializer;
    this.metrics = metrics;
  }

  @KafkaListener(
      topics = "${launchforge.distribution.topic:launchforge.config.revision-published.v1}",
      groupId = "${launchforge.distribution.consumer-group:launchforge-config-projector-v1}")
  public void project(ConsumerRecord<String, String> record) {
    ConfigRevisionPublishedEvent event = codec.decode(record.value());
    if (!event.environmentId().toString().equals(record.key())) {
      throw new IllegalArgumentException("Kafka key does not match the event environment");
    }
    Optional<AuthoritativeSnapshot> stored =
        repository.findRevision(event.environmentId(), event.revision());
    if (stored.isEmpty()) {
      throw new IllegalStateException("Published revision is not present in PostgreSQL");
    }
    AuthoritativeSnapshot snapshot = stored.orElseThrow();
    if (!snapshot.organizationId().equals(event.organizationId())
        || !snapshot.projectId().equals(event.projectId())
        || !snapshot.checksum().equals(event.snapshotChecksum())) {
      throw new IllegalArgumentException("Event metadata does not match PostgreSQL");
    }
    if (snapshot.currentRevision() != snapshot.revision()) {
      metrics.projectionIgnored();
      return;
    }
    validator.validate(snapshot);
    if (materializer.materialize(snapshot)) {
      metrics.projectionAdvanced();
    } else {
      metrics.projectionIgnored();
    }
  }
}
