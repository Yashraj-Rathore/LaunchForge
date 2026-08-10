package dev.launchforge.domain.controlplane;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record FlagDefinition(
    FlagId id,
    ProjectId projectId,
    ResourceKey key,
    String name,
    FlagType type,
    boolean clientVisible,
    List<Variation> variations,
    Status status,
    long version,
    Instant createdAt,
    Instant updatedAt) {
  public static final int MIN_VARIATIONS = 2;
  public static final int MAX_VARIATIONS = 10;

  public FlagDefinition {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(projectId, "projectId");
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    if (name == null || name.isBlank() || !name.equals(name.strip()) || name.length() > 120) {
      throw new ControlPlaneRuleViolationException("Flag name is invalid");
    }
    variations = List.copyOf(Objects.requireNonNull(variations, "variations"));
    if (variations.size() < MIN_VARIATIONS || variations.size() > MAX_VARIATIONS) {
      throw new ControlPlaneRuleViolationException("A flag requires between 2 and 10 variations");
    }
    Set<UUID> ids = new HashSet<>();
    Set<ResourceKey> keys = new HashSet<>();
    for (Variation variation : variations) {
      if (!ids.add(variation.id()) || !keys.add(variation.key())) {
        throw new ControlPlaneRuleViolationException("Variation IDs and keys must be unique");
      }
      if (variation.value().type() != type) {
        throw new ControlPlaneRuleViolationException("Variation value does not match flag type");
      }
    }
    if (version < 0 || updatedAt.isBefore(createdAt)) {
      throw new ControlPlaneRuleViolationException("Flag version or timestamps are invalid");
    }
  }

  public boolean hasVariation(UUID variationId) {
    return variations.stream().anyMatch(variation -> variation.id().equals(variationId));
  }

  public enum FlagType {
    BOOLEAN,
    STRING,
    NUMBER,
    JSON
  }

  public enum Status {
    ACTIVE,
    ARCHIVED
  }

  public record Variation(UUID id, ResourceKey key, String name, FlagValue value) {
    public Variation {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(value, "value");
      if (name == null || name.isBlank() || !name.equals(name.strip()) || name.length() > 120) {
        throw new ControlPlaneRuleViolationException("Variation name is invalid");
      }
    }
  }

  public record FlagValue(FlagType type, String canonicalValue) {
    private static final BigDecimal MAX_SAFE_INTEGER = new BigDecimal("9007199254740991");
    public static final int MAX_VALUE_LENGTH = 65_536;

    public FlagValue {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(canonicalValue, "canonicalValue");
      if (canonicalValue.length() > MAX_VALUE_LENGTH) {
        throw new ControlPlaneRuleViolationException("Variation value is too large");
      }
      canonicalValue = normalize(type, canonicalValue);
    }

    public static FlagValue bool(boolean value) {
      return new FlagValue(FlagType.BOOLEAN, Boolean.toString(value));
    }

    public static FlagValue string(String value) {
      return new FlagValue(FlagType.STRING, value);
    }

    public static FlagValue number(BigDecimal value) {
      Objects.requireNonNull(value, "value");
      return new FlagValue(FlagType.NUMBER, value.toPlainString());
    }

    public static FlagValue json(String canonicalJson) {
      return new FlagValue(FlagType.JSON, canonicalJson);
    }

    private static String normalize(FlagType type, String value) {
      return switch (type) {
        case BOOLEAN -> {
          if (!value.equals("true") && !value.equals("false")) {
            throw new ControlPlaneRuleViolationException("Boolean variation must be true or false");
          }
          yield value;
        }
        case STRING -> value;
        case NUMBER -> normalizeNumber(value);
        case JSON -> {
          if (value.isBlank()) {
            throw new ControlPlaneRuleViolationException("JSON variation cannot be blank");
          }
          yield value;
        }
      };
    }

    private static String normalizeNumber(String value) {
      try {
        BigDecimal number = new BigDecimal(value);
        if (!Double.isFinite(number.doubleValue())) {
          throw new ControlPlaneRuleViolationException("Number must be a finite binary64 value");
        }
        if (number.scale() <= 0 && number.abs().compareTo(MAX_SAFE_INTEGER) > 0) {
          throw new ControlPlaneRuleViolationException(
              "Integer exceeds the interoperable safe range");
        }
        if (number.signum() == 0) {
          return "0";
        }
        return number.stripTrailingZeros().toPlainString();
      } catch (NumberFormatException exception) {
        throw new ControlPlaneRuleViolationException("Number variation is invalid");
      }
    }
  }
}
