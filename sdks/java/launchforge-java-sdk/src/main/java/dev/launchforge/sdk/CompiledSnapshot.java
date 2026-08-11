package dev.launchforge.sdk;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, validated representation used by the local evaluation hot path. */
public final class CompiledSnapshot {
  private final int schemaVersion;
  private final int algorithmVersion;
  private final String projectKey;
  private final String environmentKey;
  private final long revision;
  private final Instant generatedAt;
  private final String checksum;
  private final Map<String, CompiledFlag> flags;

  CompiledSnapshot(
      int schemaVersion,
      int algorithmVersion,
      String projectKey,
      String environmentKey,
      long revision,
      Instant generatedAt,
      String checksum,
      Map<String, CompiledFlag> flags) {
    this.schemaVersion = schemaVersion;
    this.algorithmVersion = algorithmVersion;
    this.projectKey = Objects.requireNonNull(projectKey, "projectKey");
    this.environmentKey = Objects.requireNonNull(environmentKey, "environmentKey");
    this.revision = revision;
    this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
    this.checksum = Objects.requireNonNull(checksum, "checksum");
    this.flags = Map.copyOf(flags);
  }

  public int schemaVersion() {
    return schemaVersion;
  }

  public int algorithmVersion() {
    return algorithmVersion;
  }

  public String projectKey() {
    return projectKey;
  }

  public String environmentKey() {
    return environmentKey;
  }

  public long revision() {
    return revision;
  }

  public Instant generatedAt() {
    return generatedAt;
  }

  public String checksum() {
    return checksum;
  }

  CompiledFlag flag(String key) {
    return flags.get(key);
  }

  record CompiledFlag(
      FlagType type,
      boolean enabled,
      boolean clientVisible,
      Map<String, Object> variations,
      String offVariation,
      String defaultVariation,
      List<CompiledRule> rules,
      CompiledRollout rollout) {
    CompiledFlag {
      Objects.requireNonNull(type, "type");
      variations = Map.copyOf(variations);
      Objects.requireNonNull(offVariation, "offVariation");
      Objects.requireNonNull(defaultVariation, "defaultVariation");
      rules = List.copyOf(rules);
    }
  }

  record CompiledRule(String id, List<CompiledCondition> conditions, String variation) {
    CompiledRule {
      Objects.requireNonNull(id, "id");
      conditions = List.copyOf(conditions);
      Objects.requireNonNull(variation, "variation");
    }
  }

  record CompiledCondition(
      String attribute, AttributeType attributeType, Operator operator, List<Object> values) {
    CompiledCondition {
      Objects.requireNonNull(attribute, "attribute");
      Objects.requireNonNull(attributeType, "attributeType");
      Objects.requireNonNull(operator, "operator");
      values = List.copyOf(values);
    }
  }

  record CompiledRollout(String attribute, String salt, List<CompiledAllocation> allocations) {
    CompiledRollout {
      Objects.requireNonNull(attribute, "attribute");
      Objects.requireNonNull(salt, "salt");
      allocations = List.copyOf(allocations);
    }
  }

  record CompiledAllocation(String variation, int upperExclusive) {
    CompiledAllocation {
      Objects.requireNonNull(variation, "variation");
    }
  }

  enum AttributeType {
    STRING,
    NUMBER,
    BOOLEAN,
    SEMVER
  }

  enum Operator {
    EQUALS(AttributeType.STRING),
    NOT_EQUALS(AttributeType.STRING),
    IN(AttributeType.STRING),
    NOT_IN(AttributeType.STRING),
    STARTS_WITH(AttributeType.STRING),
    ENDS_WITH(AttributeType.STRING),
    CONTAINS(AttributeType.STRING),
    EQ(AttributeType.NUMBER),
    NE(AttributeType.NUMBER),
    GT(AttributeType.NUMBER),
    GTE(AttributeType.NUMBER),
    LT(AttributeType.NUMBER),
    LTE(AttributeType.NUMBER),
    BETWEEN_INCLUSIVE(AttributeType.NUMBER),
    IS_TRUE(AttributeType.BOOLEAN),
    IS_FALSE(AttributeType.BOOLEAN),
    SEMVER_EQ(AttributeType.SEMVER),
    SEMVER_GT(AttributeType.SEMVER),
    SEMVER_GTE(AttributeType.SEMVER),
    SEMVER_LT(AttributeType.SEMVER),
    SEMVER_LTE(AttributeType.SEMVER),
    EXISTS,
    NOT_EXISTS;

    private final AttributeType requiredType;

    Operator() {
      requiredType = null;
    }

    Operator(AttributeType requiredType) {
      this.requiredType = requiredType;
    }

    boolean supports(AttributeType attributeType) {
      return requiredType == null || requiredType == attributeType;
    }
  }
}
