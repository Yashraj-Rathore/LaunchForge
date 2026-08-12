package dev.launchforge.controlapi.simulation;

import dev.launchforge.application.controlplane.ControlPlaneService;
import dev.launchforge.application.controlplane.ControlPlaneService.DraftPreview;
import dev.launchforge.domain.controlplane.EnvironmentId;
import dev.launchforge.domain.controlplane.FlagDefinition.FlagType;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.sdk.CompiledSnapshot;
import dev.launchforge.sdk.EvaluationContext;
import dev.launchforge.sdk.EvaluationDetail;
import dev.launchforge.sdk.Evaluator;
import dev.launchforge.sdk.JsonValue;
import dev.launchforge.sdk.SnapshotParser;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Executes a draft preview through the exact pure evaluator used by the Java SDK. */
@Service
public final class EvaluationSimulationService {
  private static final BigDecimal MAX_SAFE_INTEGER = new BigDecimal("9007199254740991");

  private final ControlPlaneService controlPlaneService;
  private final ObjectMapper objectMapper;

  public EvaluationSimulationService(
      ControlPlaneService controlPlaneService, ObjectMapper objectMapper) {
    this.controlPlaneService = controlPlaneService;
    this.objectMapper = objectMapper;
  }

  public SimulationResult simulate(
      OidcIdentity actor,
      EnvironmentId environmentId,
      String flagKey,
      FlagType type,
      JsonNode defaultValue,
      String contextKey,
      Map<String, JsonNode> attributes) {
    Objects.requireNonNull(flagKey, "flagKey");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(defaultValue, "defaultValue");
    DraftPreview preview = controlPlaneService.previewDraft(actor, environmentId);
    CompiledSnapshot snapshot = SnapshotParser.parse(preview.canonicalSnapshot());
    EvaluationContext context = context(contextKey, attributes);
    EvaluationDetail<?> detail = evaluate(snapshot, flagKey, type, context, defaultValue);
    return new SimulationResult(
        flagKey,
        valueNode(detail.value()),
        detail.variationId().orElse(null),
        detail.reason().name(),
        detail.matchedRuleId().orElse(null),
        detail.rolloutBucket().isPresent() ? detail.rolloutBucket().getAsInt() : null,
        detail.errorKind().map(Enum::name).orElse(null),
        preview.currentPublishedRevision(),
        preview.candidateRevision(),
        "DRAFT");
  }

  private static EvaluationContext context(String contextKey, Map<String, JsonNode> attributes) {
    EvaluationContext.Builder builder = EvaluationContext.builder(contextKey);
    if (attributes == null) {
      return builder.build();
    }
    attributes.forEach(
        (name, value) -> {
          if (value == null || value.isNull()) {
            builder.attribute(name, (String) null);
          } else if (value.isString()) {
            builder.attribute(name, value.stringValue());
          } else if (value.isBoolean()) {
            builder.attribute(name, value.booleanValue());
          } else if (value.isNumber()) {
            BigDecimal decimal = value.decimalValue();
            if (decimal.stripTrailingZeros().scale() <= 0
                && decimal.abs().compareTo(MAX_SAFE_INTEGER) > 0) {
              throw new IllegalArgumentException("Context integer exceeds the safe range");
            }
            builder.attribute(name, value.doubleValue());
          } else {
            throw new IllegalArgumentException("Context attributes must be scalar values");
          }
        });
    return builder.build();
  }

  private static EvaluationDetail<?> evaluate(
      CompiledSnapshot snapshot,
      String flagKey,
      FlagType type,
      EvaluationContext context,
      JsonNode defaultValue) {
    return switch (type) {
      case BOOLEAN -> {
        if (!defaultValue.isBoolean()) {
          throw new IllegalArgumentException("Boolean simulation default must be boolean");
        }
        yield Evaluator.evaluateBoolean(snapshot, flagKey, context, defaultValue.booleanValue());
      }
      case STRING -> {
        if (!defaultValue.isString()) {
          throw new IllegalArgumentException("String simulation default must be string");
        }
        yield Evaluator.evaluateString(snapshot, flagKey, context, defaultValue.stringValue());
      }
      case NUMBER -> {
        if (!defaultValue.isNumber()) {
          throw new IllegalArgumentException("Number simulation default must be number");
        }
        yield Evaluator.evaluateNumber(snapshot, flagKey, context, defaultValue.doubleValue());
      }
      case JSON ->
          Evaluator.evaluateJson(
              snapshot, flagKey, context, JsonValue.parse(defaultValue.toString()));
    };
  }

  private JsonNode valueNode(Object value) {
    if (value instanceof JsonValue json) {
      return objectMapper.readTree(json.canonicalJson());
    }
    return objectMapper.valueToTree(value);
  }

  public record SimulationResult(
      String flagKey,
      JsonNode value,
      String variationId,
      String reason,
      String matchedRuleId,
      Integer rolloutBucket,
      String errorKind,
      long currentPublishedRevision,
      long candidateRevision,
      String configuration) {}
}
