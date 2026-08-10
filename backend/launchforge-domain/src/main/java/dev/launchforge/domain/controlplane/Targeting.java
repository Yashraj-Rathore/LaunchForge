package dev.launchforge.domain.controlplane;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Targeting {
  public static final int ALGORITHM_VERSION = 1;
  public static final int BUCKET_COUNT = 100_000;
  public static final int MAX_RULES = 100;
  public static final int MAX_CONDITIONS = 10;
  public static final int MAX_CONDITION_VALUES = 50;
  public static final int MAX_ROLLOUT_ALLOCATIONS = 50;
  private static final Pattern ATTRIBUTE = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{0,63}$");
  private static final Pattern SEMVER =
      Pattern.compile(
          "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
              + "(?:-((?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*)"
              + "(?:\\.(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*))*))?"
              + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$");

  private Targeting() {}

  public enum AttributeType {
    STRING,
    NUMBER,
    BOOLEAN,
    SEMVER
  }

  public enum Operator {
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
    EXISTS(EnumSet.allOf(AttributeType.class)),
    NOT_EXISTS(EnumSet.allOf(AttributeType.class));

    private final Set<AttributeType> supportedTypes;

    Operator(AttributeType supportedType) {
      this.supportedTypes = Set.of(supportedType);
    }

    Operator(Set<AttributeType> supportedTypes) {
      this.supportedTypes = Set.copyOf(supportedTypes);
    }

    public boolean supports(AttributeType type) {
      return supportedTypes.contains(type);
    }
  }

  public record Condition(
      String attribute, AttributeType attributeType, Operator operator, List<String> values) {
    public Condition {
      attribute = requireAttribute(attribute);
      Objects.requireNonNull(attributeType, "attributeType");
      Objects.requireNonNull(operator, "operator");
      values = List.copyOf(Objects.requireNonNull(values, "values"));
      if (!operator.supports(attributeType)) {
        throw new ControlPlaneRuleViolationException("Operator is invalid for attribute type");
      }
      validateArity(operator, values.size());
      for (String value : values) {
        validateConditionValue(attributeType, value);
      }
    }
  }

  public record Rule(UUID id, String name, List<Condition> conditions, UUID variationId) {
    public Rule {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(variationId, "variationId");
      if (name == null || name.isBlank() || !name.equals(name.strip()) || name.length() > 120) {
        throw new ControlPlaneRuleViolationException("Rule name is invalid");
      }
      conditions = List.copyOf(Objects.requireNonNull(conditions, "conditions"));
      if (conditions.isEmpty() || conditions.size() > MAX_CONDITIONS) {
        throw new ControlPlaneRuleViolationException("A rule requires between 1 and 10 conditions");
      }
    }
  }

  public record Allocation(UUID variationId, int weight) {
    public Allocation {
      Objects.requireNonNull(variationId, "variationId");
      if (weight <= 0 || weight > BUCKET_COUNT) {
        throw new ControlPlaneRuleViolationException("Rollout weight is outside its valid range");
      }
    }
  }

  public record PercentageRollout(String subjectAttribute, List<Allocation> allocations) {
    public PercentageRollout {
      subjectAttribute = requireAttribute(subjectAttribute);
      allocations = List.copyOf(Objects.requireNonNull(allocations, "allocations"));
      if (allocations.isEmpty() || allocations.size() > MAX_ROLLOUT_ALLOCATIONS) {
        throw new ControlPlaneRuleViolationException("Rollout allocation count is invalid");
      }
      Set<UUID> variationIds = new HashSet<>();
      long total = 0;
      for (Allocation allocation : allocations) {
        if (!variationIds.add(allocation.variationId())) {
          throw new ControlPlaneRuleViolationException("Rollout variation IDs must be unique");
        }
        total += allocation.weight();
      }
      if (total != BUCKET_COUNT) {
        throw new ControlPlaneRuleViolationException("Rollout weights must total 100000");
      }
    }
  }

  private static String requireAttribute(String value) {
    if (value == null || !ATTRIBUTE.matcher(value).matches()) {
      throw new ControlPlaneRuleViolationException("Targeting attribute is invalid");
    }
    return value;
  }

  private static void validateArity(Operator operator, int count) {
    int minimum;
    int maximum;
    switch (operator) {
      case EXISTS, NOT_EXISTS, IS_TRUE, IS_FALSE -> {
        minimum = 0;
        maximum = 0;
      }
      case BETWEEN_INCLUSIVE -> {
        minimum = 2;
        maximum = 2;
      }
      case IN, NOT_IN -> {
        minimum = 1;
        maximum = MAX_CONDITION_VALUES;
      }
      default -> {
        minimum = 1;
        maximum = 1;
      }
    }
    if (count < minimum || count > maximum) {
      throw new ControlPlaneRuleViolationException("Condition value count is invalid");
    }
  }

  private static void validateConditionValue(AttributeType type, String value) {
    if (value == null || value.length() > 1024) {
      throw new ControlPlaneRuleViolationException("Condition value is invalid");
    }
    try {
      switch (type) {
        case NUMBER -> {
          BigDecimal number = new BigDecimal(value);
          if (!Double.isFinite(number.doubleValue())) {
            throw new ControlPlaneRuleViolationException("Numeric condition must be finite");
          }
        }
        case SEMVER -> {
          if (!SEMVER.matcher(value).matches()) {
            throw new ControlPlaneRuleViolationException("Semantic version is invalid");
          }
        }
        case BOOLEAN ->
            throw new ControlPlaneRuleViolationException(
                "Boolean conditions do not accept configured values");
        case STRING -> {
          // Strings are deliberately exact and are never implicitly coerced.
        }
      }
    } catch (NumberFormatException exception) {
      throw new ControlPlaneRuleViolationException("Numeric condition value is invalid");
    }
  }
}
