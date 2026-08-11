package dev.launchforge.sdk;

import dev.launchforge.sdk.CompiledSnapshot.AttributeType;
import dev.launchforge.sdk.CompiledSnapshot.CompiledAllocation;
import dev.launchforge.sdk.CompiledSnapshot.CompiledCondition;
import dev.launchforge.sdk.CompiledSnapshot.CompiledFlag;
import dev.launchforge.sdk.CompiledSnapshot.CompiledRollout;
import dev.launchforge.sdk.CompiledSnapshot.CompiledRule;
import dev.launchforge.sdk.CompiledSnapshot.Operator;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.erdtman.jcs.JsonCanonicalizer;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.node.ObjectNode;

/** Strict snapshot-schema parser. A snapshot is compiled completely before it can be activated. */
public final class SnapshotParser {
  public static final int SCHEMA_VERSION = 1;
  public static final int ALGORITHM_VERSION = 1;
  public static final int MAX_SNAPSHOT_BYTES = 5 * 1024 * 1024;
  public static final int MAX_FLAGS = 2_000;
  public static final int MAX_VARIATIONS = 50;
  public static final int MAX_RULES = 100;
  public static final int MAX_CONDITIONS = 10;
  public static final int MAX_CONDITION_VALUES = 50;
  public static final int MAX_ROLLOUT_ALLOCATIONS = 50;
  public static final int MAX_JSON_VALUE_BYTES = 64 * 1024;
  private static final BigDecimal MAX_SAFE_INTEGER = new BigDecimal("9007199254740991");
  private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9._-]{0,63}$");
  private static final Pattern ATTRIBUTE = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{0,63}$");
  private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}$");
  private static final Pattern SALT = Pattern.compile("^[A-Za-z0-9_-]{16,128}$");
  private static final ObjectMapper MAPPER =
      new ObjectMapper(
          JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
  private static final ObjectReader STRICT_READER =
      MAPPER.reader(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  private SnapshotParser() {}

  public static CompiledSnapshot parse(String json) {
    if (json == null) {
      throw invalid("Snapshot is absent");
    }
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_SNAPSHOT_BYTES) {
      throw invalid("Snapshot exceeds 5 MiB");
    }
    try {
      JsonNode parsed = STRICT_READER.readTree(json);
      if (!(parsed instanceof ObjectNode root)) {
        throw invalid("Snapshot root must be an object");
      }
      validateJsonValue(root, 0);
      int schemaVersion = requiredInt(root, "schemaVersion");
      int algorithmVersion = requiredInt(root, "algorithmVersion");
      if (schemaVersion != SCHEMA_VERSION || algorithmVersion != ALGORITHM_VERSION) {
        throw invalid("Snapshot schema or algorithm version is unsupported");
      }
      String projectKey = requiredKey(root, "projectKey");
      String environmentKey = requiredKey(root, "environmentKey");
      long revision = requiredPositiveLong(root, "revision");
      Instant generatedAt = requiredInstant(root, "generatedAt");
      String checksum = requiredText(root, "checksum");
      if (!CHECKSUM.matcher(checksum).matches() || !checksum.equals(calculateChecksum(root))) {
        throw invalid("Snapshot checksum is invalid");
      }
      Map<String, CompiledFlag> flags = parseFlags(requiredObject(root, "flags"));
      return new CompiledSnapshot(
          schemaVersion,
          algorithmVersion,
          projectKey,
          environmentKey,
          revision,
          generatedAt,
          checksum,
          flags);
    } catch (SnapshotValidationException exception) {
      throw exception;
    } catch (JacksonException exception) {
      throw invalid("Snapshot is not valid JSON", exception);
    } catch (RuntimeException exception) {
      throw invalid("Snapshot is not valid version-1 configuration", exception);
    }
  }

  public static CompiledSnapshot parse(byte[] bytes) {
    if (bytes == null || bytes.length > MAX_SNAPSHOT_BYTES) {
      throw invalid("Snapshot is absent or exceeds 5 MiB");
    }
    try {
      String json =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString();
      return parse(json);
    } catch (CharacterCodingException exception) {
      throw invalid("Snapshot is not valid UTF-8", exception);
    }
  }

  static JsonValue parseStandaloneJsonValue(String json) {
    if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_VALUE_BYTES) {
      throw invalid("JSON value is absent or too large");
    }
    try {
      JsonNode parsed = STRICT_READER.readTree(json);
      if (parsed == null) {
        throw invalid("JSON value is absent");
      }
      validateJsonValue(parsed, 0);
      String canonical = canonicalizeValue(MAPPER.writeValueAsString(parsed));
      if (canonical.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_VALUE_BYTES) {
        throw invalid("Canonical JSON value exceeds 64 KiB");
      }
      return new JsonValue(canonical);
    } catch (SnapshotValidationException exception) {
      throw exception;
    } catch (JacksonException exception) {
      throw invalid("JSON value is invalid", exception);
    }
  }

  private static Map<String, CompiledFlag> parseFlags(ObjectNode flagsNode) {
    if (flagsNode.size() > MAX_FLAGS) {
      throw invalid("Snapshot has too many flags");
    }
    Map<String, CompiledFlag> flags = new LinkedHashMap<>();
    flagsNode
        .properties()
        .forEach(
            entry -> {
              requireKey(entry.getKey(), "Flag key");
              if (!(entry.getValue() instanceof ObjectNode flagNode)) {
                throw invalid("Flag must be an object");
              }
              flags.put(entry.getKey(), parseFlag(flagNode));
            });
    return flags;
  }

  private static CompiledFlag parseFlag(ObjectNode node) {
    FlagType type = FlagType.fromWireValue(requiredText(node, "type"));
    boolean enabled = requiredBoolean(node, "enabled");
    boolean clientVisible = requiredBoolean(node, "clientVisible");
    Map<String, Object> variations = parseVariations(requiredArray(node, "variations"), type);
    String offVariation = requiredVariationReference(node, "offVariation", variations);
    String defaultVariation = requiredVariationReference(node, "defaultVariation", variations);
    List<CompiledRule> rules = parseRules(requiredArray(node, "rules"), variations);
    CompiledRollout rollout =
        node.get("rollout") == null ? null : parseRollout(node.get("rollout"), variations);
    return new CompiledFlag(
        type, enabled, clientVisible, variations, offVariation, defaultVariation, rules, rollout);
  }

  private static Map<String, Object> parseVariations(JsonNode node, FlagType type) {
    if (node.isEmpty() || node.size() > MAX_VARIATIONS) {
      throw invalid("Variation count is invalid");
    }
    Map<String, Object> variations = new LinkedHashMap<>();
    for (JsonNode item : node) {
      if (!(item instanceof ObjectNode variation)) {
        throw invalid("Variation must be an object");
      }
      String id = requiredKey(variation, "id");
      Object value = parseVariationValue(required(variation, "value"), type);
      if (variations.put(id, value) != null) {
        throw invalid("Variation IDs must be unique");
      }
    }
    return variations;
  }

  private static Object parseVariationValue(JsonNode value, FlagType type) {
    return switch (type) {
      case BOOLEAN -> {
        if (!value.isBoolean()) {
          throw invalid("Boolean variation value has the wrong type");
        }
        yield value.booleanValue();
      }
      case STRING -> {
        if (!value.isString()) {
          throw invalid("String variation value has the wrong type");
        }
        yield requireUnicode(value.stringValue(), "String variation value");
      }
      case NUMBER -> requireNumber(value, "Number variation value");
      case JSON -> parseStandaloneJsonValue(writeJson(value));
    };
  }

  private static List<CompiledRule> parseRules(JsonNode node, Map<String, Object> variations) {
    if (node.size() > MAX_RULES) {
      throw invalid("Flag has too many rules");
    }
    List<CompiledRule> rules = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (JsonNode item : node) {
      if (!(item instanceof ObjectNode rule)) {
        throw invalid("Rule must be an object");
      }
      String id = requiredText(rule, "id");
      if (id.isBlank() || id.length() > 128 || !ids.add(id)) {
        throw invalid("Rule ID is invalid or duplicated");
      }
      JsonNode conditionsNode = requiredArray(rule, "conditions");
      if (conditionsNode.isEmpty() || conditionsNode.size() > MAX_CONDITIONS) {
        throw invalid("Rule condition count is invalid");
      }
      List<CompiledCondition> conditions = new ArrayList<>();
      conditionsNode.forEach(condition -> conditions.add(parseCondition(condition)));
      String variation = requiredVariationReference(rule, "variation", variations);
      rules.add(new CompiledRule(id, conditions, variation));
    }
    return rules;
  }

  private static CompiledCondition parseCondition(JsonNode node) {
    if (!(node instanceof ObjectNode condition)) {
      throw invalid("Condition must be an object");
    }
    String attribute = requiredText(condition, "attribute");
    if (!ATTRIBUTE.matcher(attribute).matches()) {
      throw invalid("Targeting attribute is invalid");
    }
    AttributeType attributeType;
    Operator operator;
    try {
      attributeType = parseAttributeType(requiredText(condition, "attributeType"));
      operator = Operator.valueOf(requiredText(condition, "operator"));
    } catch (IllegalArgumentException exception) {
      throw invalid("Condition type or operator is unsupported");
    }
    if (!operator.supports(attributeType)) {
      throw invalid("Operator is invalid for attribute type");
    }
    JsonNode valuesNode = requiredArray(condition, "values");
    validateArity(operator, valuesNode.size());
    List<Object> values = new ArrayList<>();
    valuesNode.forEach(value -> values.add(parseConditionValue(attributeType, value)));
    return new CompiledCondition(attribute, attributeType, operator, values);
  }

  private static Object parseConditionValue(AttributeType type, JsonNode value) {
    return switch (type) {
      case STRING -> {
        if (!value.isString()) {
          throw invalid("String condition value has the wrong type");
        }
        String text = requireUnicode(value.stringValue(), "String condition value");
        if (text.codePointCount(0, text.length()) > 1024) {
          throw invalid("String condition value is too long");
        }
        yield text;
      }
      case NUMBER -> requireNumber(value, "Number condition value");
      case BOOLEAN -> throw invalid("Boolean conditions do not accept configured values");
      case SEMVER -> {
        if (!value.isString()) {
          throw invalid("Semantic version condition value has the wrong type");
        }
        try {
          yield SemanticVersion.parse(value.stringValue());
        } catch (IllegalArgumentException exception) {
          throw invalid("Semantic version condition value is invalid");
        }
      }
    };
  }

  private static CompiledRollout parseRollout(JsonNode node, Map<String, Object> variations) {
    if (!(node instanceof ObjectNode rollout)) {
      throw invalid("Rollout must be an object");
    }
    String attribute = requiredText(rollout, "attribute");
    if (!ATTRIBUTE.matcher(attribute).matches()) {
      throw invalid("Rollout attribute is invalid");
    }
    String salt = requiredText(rollout, "salt");
    if (!SALT.matcher(salt).matches()) {
      throw invalid("Rollout salt is invalid");
    }
    JsonNode weights = requiredArray(rollout, "weights");
    if (weights.isEmpty() || weights.size() > MAX_ROLLOUT_ALLOCATIONS) {
      throw invalid("Rollout allocation count is invalid");
    }
    List<CompiledAllocation> allocations = new ArrayList<>();
    Set<String> variationIds = new HashSet<>();
    int total = 0;
    for (JsonNode item : weights) {
      if (!(item instanceof ObjectNode allocation)) {
        throw invalid("Rollout allocation must be an object");
      }
      String variation = requiredVariationReference(allocation, "variation", variations);
      if (!variationIds.add(variation)) {
        throw invalid("Rollout variation IDs must be unique");
      }
      int weight = requiredInt(allocation, "weight");
      if (weight <= 0 || weight > RolloutHasher.BUCKET_COUNT) {
        throw invalid("Rollout weight is invalid");
      }
      try {
        total = Math.addExact(total, weight);
      } catch (ArithmeticException exception) {
        throw invalid("Rollout total is invalid");
      }
      allocations.add(new CompiledAllocation(variation, total));
    }
    if (total != RolloutHasher.BUCKET_COUNT) {
      throw invalid("Rollout weights must total 100000");
    }
    return new CompiledRollout(attribute, salt, allocations);
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
      throw invalid("Condition value count is invalid");
    }
  }

  private static AttributeType parseAttributeType(String value) {
    return switch (value) {
      case "string" -> AttributeType.STRING;
      case "number" -> AttributeType.NUMBER;
      case "boolean" -> AttributeType.BOOLEAN;
      case "semver" -> AttributeType.SEMVER;
      default -> throw invalid("Condition attribute type is unsupported");
    };
  }

  private static Double requireNumber(JsonNode node, String label) {
    if (!node.isNumber()) {
      throw invalid(label + " has the wrong type");
    }
    double number = node.doubleValue();
    BigDecimal decimal = node.decimalValue();
    boolean mathematicalInteger = decimal.stripTrailingZeros().scale() <= 0;
    if (!Double.isFinite(number)
        || (mathematicalInteger && decimal.abs().compareTo(MAX_SAFE_INTEGER) > 0)) {
      throw invalid(label + " is outside finite safe binary64");
    }
    return number == 0.0d ? 0.0d : number;
  }

  private static void validateJsonValue(JsonNode value, int depth) {
    if (depth > 64) {
      throw invalid("JSON nesting is too deep");
    }
    if (value.isNumber()) {
      requireNumber(value, "JSON number");
    } else if (value.isString()) {
      requireUnicode(value.stringValue(), "JSON string");
    } else if (value.isObject()) {
      value
          .properties()
          .forEach(
              entry -> {
                requireUnicode(entry.getKey(), "JSON property name");
                validateJsonValue(entry.getValue(), depth + 1);
              });
    } else if (value.isArray()) {
      value.forEach(child -> validateJsonValue(child, depth + 1));
    }
  }

  private static String calculateChecksum(ObjectNode root) {
    try {
      ObjectNode copy = root.deepCopy();
      copy.remove("checksum");
      String canonical = canonicalize(MAPPER.writeValueAsString(copy));
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (JacksonException exception) {
      throw invalid("Snapshot checksum projection cannot be serialized", exception);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
    }
  }

  private static String canonicalize(String json) {
    try {
      return new JsonCanonicalizer(json).getEncodedString();
    } catch (IOException | RuntimeException exception) {
      throw invalid("JSON canonicalization failed", exception);
    }
  }

  private static String canonicalizeValue(String json) {
    String wrapper = canonicalize("{\"value\":" + json + '}');
    return wrapper.substring("{\"value\":".length(), wrapper.length() - 1);
  }

  private static String writeJson(JsonNode node) {
    try {
      return MAPPER.writeValueAsString(node);
    } catch (JacksonException exception) {
      throw invalid("JSON value cannot be serialized", exception);
    }
  }

  private static JsonNode required(ObjectNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      throw invalid("Required snapshot field is absent: " + field);
    }
    return value;
  }

  private static ObjectNode requiredObject(ObjectNode node, String field) {
    JsonNode value = required(node, field);
    if (!(value instanceof ObjectNode object)) {
      throw invalid("Snapshot field must be an object: " + field);
    }
    return object;
  }

  private static JsonNode requiredArray(ObjectNode node, String field) {
    JsonNode value = required(node, field);
    if (!value.isArray()) {
      throw invalid("Snapshot field must be an array: " + field);
    }
    return value;
  }

  private static String requiredText(ObjectNode node, String field) {
    JsonNode value = required(node, field);
    if (!value.isString()) {
      throw invalid("Snapshot field must be a string: " + field);
    }
    return requireUnicode(value.stringValue(), field);
  }

  private static String requiredKey(ObjectNode node, String field) {
    return requireKey(requiredText(node, field), field);
  }

  private static String requireKey(String value, String label) {
    if (!KEY.matcher(value).matches()) {
      throw invalid(label + " is not a canonical key");
    }
    return value;
  }

  private static String requiredVariationReference(
      ObjectNode node, String field, Map<String, Object> variations) {
    String reference = requiredKey(node, field);
    if (!variations.containsKey(reference)) {
      throw invalid("Variation reference is unknown: " + field);
    }
    return reference;
  }

  private static boolean requiredBoolean(ObjectNode node, String field) {
    JsonNode value = required(node, field);
    if (!value.isBoolean()) {
      throw invalid("Snapshot field must be a boolean: " + field);
    }
    return value.booleanValue();
  }

  private static int requiredInt(ObjectNode node, String field) {
    JsonNode value = required(node, field);
    if (!value.isInt()) {
      throw invalid("Snapshot field must be a 32-bit integer: " + field);
    }
    return value.intValue();
  }

  private static long requiredPositiveLong(ObjectNode node, String field) {
    JsonNode value = required(node, field);
    if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
      throw invalid("Snapshot field must be a positive integer: " + field);
    }
    return value.longValue();
  }

  private static Instant requiredInstant(ObjectNode node, String field) {
    try {
      return Instant.parse(requiredText(node, field));
    } catch (DateTimeException exception) {
      throw invalid("Snapshot timestamp is invalid: " + field);
    }
  }

  private static String requireUnicode(String value, String label) {
    try {
      return EvaluationContext.requireWellFormedUnicode(value, label);
    } catch (IllegalArgumentException exception) {
      throw invalid(label + " is not well-formed Unicode");
    }
  }

  private static SnapshotValidationException invalid(String message) {
    return new SnapshotValidationException(message);
  }

  private static SnapshotValidationException invalid(String message, Throwable cause) {
    return new SnapshotValidationException(message, cause);
  }
}
