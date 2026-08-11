package dev.launchforge.infrastructure.controlplane;

import dev.launchforge.application.controlplane.SnapshotCodec;
import dev.launchforge.application.organization.OrganizationAccess;
import dev.launchforge.domain.controlplane.ControlPlaneRuleViolationException;
import dev.launchforge.domain.controlplane.Environment;
import dev.launchforge.domain.controlplane.EnvironmentDraft;
import dev.launchforge.domain.controlplane.FlagDefinition;
import dev.launchforge.domain.controlplane.FlagDefinition.FlagValue;
import dev.launchforge.domain.controlplane.Project;
import dev.launchforge.domain.controlplane.Targeting;
import dev.launchforge.domain.controlplane.Targeting.Allocation;
import dev.launchforge.domain.controlplane.Targeting.Condition;
import dev.launchforge.domain.controlplane.Targeting.PercentageRollout;
import dev.launchforge.domain.controlplane.Targeting.Rule;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.erdtman.jcs.JsonCanonicalizer;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public final class JacksonSnapshotCodec implements SnapshotCodec {
  private static final BigDecimal MAX_SAFE_INTEGER = new BigDecimal("9007199254740991");
  private final ObjectMapper objectMapper;
  private final ObjectMapper strictJsonMapper;

  public JacksonSnapshotCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.strictJsonMapper =
        new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
  }

  @Override
  public EncodedSnapshot encode(
      Project project,
      Environment environment,
      List<FlagDefinition> flags,
      List<EnvironmentDraft> drafts,
      long revision,
      Instant publishedAt) {
    Map<String, EnvironmentDraft> draftsByFlag = new HashMap<>();
    drafts.forEach(draft -> draftsByFlag.put(draft.flagId().value().toString(), draft));
    Map<String, Object> encodedFlags = new LinkedHashMap<>();
    flags.stream()
        .sorted(Comparator.comparing(flag -> flag.key().value()))
        .forEach(
            flag ->
                encodedFlags.put(
                    flag.key().value(),
                    encodeFlag(flag, draftsByFlag.get(flag.id().value().toString()))));
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schemaVersion", 1);
    document.put("algorithmVersion", Targeting.ALGORITHM_VERSION);
    document.put("projectKey", project.key().value());
    document.put("environmentKey", environment.key().value());
    document.put("revision", revision);
    document.put("generatedAt", publishedAt.toString());
    document.put("flags", encodedFlags);
    return withChecksum(document);
  }

  @Override
  public EncodedSnapshot rebase(String canonicalSnapshot, long revision, Instant publishedAt) {
    try {
      JsonNode parsed = objectMapper.readTree(canonicalSnapshot);
      if (!(parsed instanceof ObjectNode root)) {
        throw new ControlPlaneRuleViolationException("Stored snapshot shape is invalid");
      }
      root.remove("checksum");
      root.put("revision", revision);
      root.put("generatedAt", publishedAt.toString());
      return withChecksum(root);
    } catch (JacksonException exception) {
      throw new ControlPlaneRuleViolationException("Stored snapshot cannot be decoded");
    }
  }

  @Override
  public RevisionDiff diff(String fromCanonicalSnapshot, String toCanonicalSnapshot) {
    Map<String, JsonNode> from = flagsByKey(fromCanonicalSnapshot);
    Map<String, JsonNode> to = flagsByKey(toCanonicalSnapshot);
    TreeSet<String> added = new TreeSet<>(to.keySet());
    added.removeAll(from.keySet());
    TreeSet<String> removed = new TreeSet<>(from.keySet());
    removed.removeAll(to.keySet());
    TreeSet<String> changed = new TreeSet<>();
    from.forEach(
        (key, value) -> {
          if (to.containsKey(key) && !value.equals(to.get(key))) {
            changed.add(key);
          }
        });
    return new RevisionDiff(List.copyOf(added), List.copyOf(removed), List.copyOf(changed));
  }

  @Override
  public String publicationEvent(
      OrganizationAccess access,
      Environment environment,
      long revision,
      String checksum,
      Instant occurredAt) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("eventId", UUID.randomUUID().toString());
    event.put("eventType", "config.revision-published.v1");
    event.put("schemaVersion", 1);
    event.put("occurredAt", occurredAt.toString());
    event.put("organizationId", access.organization().id().value().toString());
    event.put("projectId", environment.projectId().value().toString());
    event.put("environmentId", environment.id().value().toString());
    event.put("revision", revision);
    event.put("snapshotChecksum", checksum);
    event.put("traceId", UUID.randomUUID().toString());
    return canonicalString(serialize(event));
  }

  @Override
  public String canonicalizeJsonValue(String rawJson) {
    if (rawJson == null || rawJson.length() > FlagDefinition.FlagValue.MAX_VALUE_LENGTH) {
      throw new ControlPlaneRuleViolationException("JSON value is absent or too large");
    }
    try {
      JsonNode parsed =
          strictJsonMapper.reader(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(rawJson);
      validateJsonValue(parsed, 0);
      String wrapper = new JsonCanonicalizer("{\"value\":" + rawJson + '}').getEncodedString();
      return wrapper.substring("{\"value\":".length(), wrapper.length() - 1);
    } catch (IOException | RuntimeException exception) {
      throw new ControlPlaneRuleViolationException("JSON value is not valid I-JSON");
    }
  }

  private Map<String, Object> encodeFlag(FlagDefinition flag, EnvironmentDraft draft) {
    if (draft == null) {
      throw new ControlPlaneRuleViolationException("Published flag draft is missing");
    }
    Map<String, Object> encoded = new LinkedHashMap<>();
    encoded.put("type", flag.type().name().toLowerCase(Locale.ROOT));
    encoded.put("enabled", draft.enabled());
    encoded.put("clientVisible", flag.clientVisible());
    encoded.put(
        "variations",
        flag.variations().stream()
            .map(
                variation ->
                    Map.of(
                        "id", variation.key().value(),
                        "value", typedValue(variation.value())))
            .toList());
    encoded.put("offVariation", variationKey(flag, draft.offVariationId()));
    encoded.put("defaultVariation", variationKey(flag, draft.fallthroughVariationId()));
    encoded.put("rules", draft.rules().stream().map(rule -> encodeRule(flag, rule)).toList());
    if (draft.rollout() != null) {
      encoded.put("rollout", encodeRollout(flag, draft));
    }
    return encoded;
  }

  private Map<String, Object> encodeRule(FlagDefinition flag, Rule rule) {
    return Map.of(
        "id", rule.id().toString(),
        "conditions", rule.conditions().stream().map(this::encodeCondition).toList(),
        "variation", variationKey(flag, rule.variationId()));
  }

  private Map<String, Object> encodeCondition(Condition condition) {
    return Map.of(
        "attribute", condition.attribute(),
        "attributeType", condition.attributeType().name().toLowerCase(Locale.ROOT),
        "operator", condition.operator().name(),
        "values", conditionValues(condition));
  }

  private Object encodeRollout(FlagDefinition flag, EnvironmentDraft draft) {
    PercentageRollout rollout = draft.rollout();
    return Map.of(
        "attribute", rollout.subjectAttribute(),
        "salt", draft.rolloutSalt(),
        "weights",
            rollout.allocations().stream()
                .map(allocation -> encodeAllocation(flag, allocation))
                .toList());
  }

  private Map<String, Object> encodeAllocation(FlagDefinition flag, Allocation allocation) {
    return Map.of(
        "variation", variationKey(flag, allocation.variationId()), "weight", allocation.weight());
  }

  private Object typedValue(FlagValue value) {
    try {
      return switch (value.type()) {
        case BOOLEAN -> Boolean.valueOf(value.canonicalValue());
        case STRING -> value.canonicalValue();
        case NUMBER -> new java.math.BigDecimal(value.canonicalValue());
        case JSON -> objectMapper.readTree(value.canonicalValue());
      };
    } catch (JacksonException exception) {
      throw new ControlPlaneRuleViolationException("Canonical JSON variation is invalid");
    }
  }

  private Map<String, JsonNode> flagsByKey(String snapshot) {
    try {
      JsonNode flags = objectMapper.readTree(snapshot).path("flags");
      if (!flags.isObject()) {
        throw new ControlPlaneRuleViolationException("Stored snapshot flags are invalid");
      }
      Map<String, JsonNode> byKey = new HashMap<>();
      flags.properties().forEach(entry -> byKey.put(entry.getKey(), entry.getValue()));
      return byKey;
    } catch (JacksonException exception) {
      throw new ControlPlaneRuleViolationException("Stored snapshot cannot be decoded");
    }
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new ControlPlaneRuleViolationException("Snapshot cannot be serialized");
    }
  }

  private EncodedSnapshot withChecksum(Object valueWithoutChecksum) {
    try {
      ObjectNode document;
      if (valueWithoutChecksum instanceof ObjectNode objectNode) {
        document = objectNode;
      } else {
        document = (ObjectNode) objectMapper.valueToTree(valueWithoutChecksum);
      }
      document.remove("checksum");
      String withoutChecksum = canonicalString(objectMapper.writeValueAsString(document));
      String checksum = sha256(withoutChecksum);
      document.put("checksum", checksum);
      String complete = canonicalString(objectMapper.writeValueAsString(document));
      return new EncodedSnapshot(
          complete, checksum, complete.getBytes(StandardCharsets.UTF_8).length);
    } catch (JacksonException exception) {
      throw new ControlPlaneRuleViolationException("Snapshot checksum could not be encoded");
    }
  }

  private static String canonicalString(String json) {
    try {
      return new JsonCanonicalizer(json).getEncodedString();
    } catch (IOException | RuntimeException exception) {
      throw new ControlPlaneRuleViolationException("Snapshot canonicalization failed");
    }
  }

  private static String sha256(String canonicalJson) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
    }
  }

  private static String variationKey(FlagDefinition flag, UUID variationId) {
    return flag.variations().stream()
        .filter(variation -> variation.id().equals(variationId))
        .map(variation -> variation.key().value())
        .findFirst()
        .orElseThrow(
            () -> new ControlPlaneRuleViolationException("Variation reference is invalid"));
  }

  private static List<Object> conditionValues(Condition condition) {
    return condition.values().stream()
        .map(
            value ->
                condition.attributeType() == Targeting.AttributeType.NUMBER
                    ? (Object) new java.math.BigDecimal(value)
                    : value)
        .toList();
  }

  private static void validateJsonValue(JsonNode value, int depth) {
    if (depth > 64) {
      throw new ControlPlaneRuleViolationException("JSON value nesting is too deep");
    }
    if (value.isNumber()) {
      if (!Double.isFinite(value.doubleValue())) {
        throw new ControlPlaneRuleViolationException("JSON number must be finite binary64");
      }
      BigDecimal decimal = value.decimalValue();
      if (decimal.stripTrailingZeros().scale() <= 0
          && decimal.abs().compareTo(MAX_SAFE_INTEGER) > 0) {
        throw new ControlPlaneRuleViolationException("JSON integer exceeds the safe range");
      }
    } else if (value.isString()) {
      requireWellFormedUnicode(value.stringValue());
    } else if (value.isObject()) {
      value
          .properties()
          .forEach(
              entry -> {
                requireWellFormedUnicode(entry.getKey());
                validateJsonValue(entry.getValue(), depth + 1);
              });
    } else if (value.isArray()) {
      value.forEach(child -> validateJsonValue(child, depth + 1));
    }
  }

  private static void requireWellFormedUnicode(String value) {
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (Character.isHighSurrogate(character)) {
        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
          throw new ControlPlaneRuleViolationException("JSON string contains unpaired surrogate");
        }
        index++;
      } else if (Character.isLowSurrogate(character)) {
        throw new ControlPlaneRuleViolationException("JSON string contains unpaired surrogate");
      }
    }
  }
}
