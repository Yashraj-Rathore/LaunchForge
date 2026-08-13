package dev.launchforge.configedge.stream;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class RevisionSignalBus {
  private static final int MAXIMUM_HINT_BYTES = 512;
  private final ObjectMapper objectMapper;
  private final Sinks.Many<RevisionHint> hints = Sinks.many().multicast().directBestEffort();
  private final Counter accepted;
  private final Counter rejected;

  public RevisionSignalBus(ObjectMapper objectMapper, MeterRegistry registry) {
    this.objectMapper = objectMapper;
    this.accepted = registry.counter("launchforge.edge.revision.hint", "outcome", "accepted");
    this.rejected = registry.counter("launchforge.edge.revision.hint", "outcome", "rejected");
  }

  public void accept(String json) {
    try {
      if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_HINT_BYTES) {
        throw new IllegalArgumentException("Revision hint is too large");
      }
      JsonNode root = objectMapper.readTree(json);
      UUID environmentId = UUID.fromString(root.path("environmentId").stringValue());
      long revision = root.path("revision").longValue();
      if (root.path("schemaVersion").intValue() != 1 || revision < 1) {
        throw new IllegalArgumentException("Revision hint is invalid");
      }
      accepted.increment();
      Sinks.EmitResult result = hints.tryEmitNext(new RevisionHint(environmentId, revision));
      if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
        rejected.increment();
      }
    } catch (RuntimeException exception) {
      rejected.increment();
    }
  }

  public Flux<RevisionHint> forEnvironment(UUID environmentId) {
    return hints.asFlux().filter(hint -> hint.environmentId().equals(environmentId));
  }

  public record RevisionHint(UUID environmentId, long revision) {}
}
