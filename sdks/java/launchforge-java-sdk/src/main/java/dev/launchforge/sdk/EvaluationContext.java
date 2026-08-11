package dev.launchforge.sdk;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable scalar evaluation context. Context values never leave the local evaluator. */
public final class EvaluationContext {
  public static final int MAX_ATTRIBUTES = 64;
  public static final int MAX_ENCODED_BYTES = 16 * 1024;
  public static final int MAX_ATTRIBUTE_NAME_CODE_POINTS = 128;
  public static final int MAX_STRING_VALUE_CODE_POINTS = 1024;
  private static final Pattern ATTRIBUTE_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{0,127}$");

  private final String key;
  private final Map<String, Object> attributes;

  private EvaluationContext(String key, Map<String, Object> attributes) {
    this.key = requireContextKey(key);
    this.attributes = Map.copyOf(attributes);
    validateEncodedSize();
  }

  public static Builder builder(String key) {
    return new Builder(key);
  }

  public String key() {
    return key;
  }

  public Map<String, Object> attributes() {
    return attributes;
  }

  Object value(String attribute) {
    return "key".equals(attribute) ? key : attributes.get(attribute);
  }

  private void validateEncodedSize() {
    StringBuilder encoded = new StringBuilder("{\"key\":");
    appendJsonString(encoded, key);
    encoded.append(",\"attributes\":{");
    boolean first = true;
    for (Map.Entry<String, Object> entry : attributes.entrySet()) {
      if (!first) {
        encoded.append(',');
      }
      first = false;
      appendJsonString(encoded, entry.getKey());
      encoded.append(':');
      if (entry.getValue() instanceof String value) {
        appendJsonString(encoded, value);
      } else {
        encoded.append(entry.getValue());
      }
    }
    encoded.append("}}");
    if (encoded.toString().getBytes(StandardCharsets.UTF_8).length > MAX_ENCODED_BYTES) {
      throw new IllegalArgumentException("Evaluation context exceeds 16 KiB");
    }
  }

  private static void appendJsonString(StringBuilder output, String value) {
    output.append('"');
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '"' -> output.append("\\\"");
        case '\\' -> output.append("\\\\");
        case '\b' -> output.append("\\b");
        case '\f' -> output.append("\\f");
        case '\n' -> output.append("\\n");
        case '\r' -> output.append("\\r");
        case '\t' -> output.append("\\t");
        default -> {
          if (character < 0x20) {
            output.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
          } else {
            output.append(character);
          }
        }
      }
    }
    output.append('"');
  }

  static String requireWellFormedUnicode(String value, String label) {
    Objects.requireNonNull(value, label);
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (Character.isHighSurrogate(character)) {
        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
          throw new IllegalArgumentException(label + " contains an unpaired surrogate");
        }
        index++;
      } else if (Character.isLowSurrogate(character)) {
        throw new IllegalArgumentException(label + " contains an unpaired surrogate");
      }
    }
    return value;
  }

  private static String requireStringValue(String value, String label) {
    requireWellFormedUnicode(value, label);
    if (value.codePointCount(0, value.length()) > MAX_STRING_VALUE_CODE_POINTS) {
      throw new IllegalArgumentException(label + " is too long");
    }
    return value;
  }

  private static String requireContextKey(String value) {
    requireStringValue(value, "Context key");
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Context key is empty");
    }
    return value;
  }

  public static final class Builder {
    private final String key;
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    private Builder(String key) {
      this.key = key;
    }

    public Builder attribute(String name, String value) {
      if (value == null) {
        attributes.remove(requireAttributeName(name));
      } else {
        attributes.put(requireAttributeName(name), requireStringValue(value, "String attribute"));
      }
      requireAttributeCount();
      return this;
    }

    public Builder attribute(String name, double value) {
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Number attribute must be finite binary64");
      }
      attributes.put(requireAttributeName(name), value == 0.0d ? 0.0d : value);
      requireAttributeCount();
      return this;
    }

    public Builder attribute(String name, boolean value) {
      attributes.put(requireAttributeName(name), value);
      requireAttributeCount();
      return this;
    }

    public EvaluationContext build() {
      return new EvaluationContext(key, attributes);
    }

    private void requireAttributeCount() {
      if (attributes.size() > MAX_ATTRIBUTES) {
        throw new IllegalArgumentException("Evaluation context has more than 64 attributes");
      }
    }

    private static String requireAttributeName(String name) {
      requireWellFormedUnicode(name, "Attribute name");
      if (name.codePointCount(0, name.length()) > MAX_ATTRIBUTE_NAME_CODE_POINTS
          || !ATTRIBUTE_NAME.matcher(name).matches()
          || "key".equals(name)) {
        throw new IllegalArgumentException("Attribute name is invalid or reserved");
      }
      return name;
    }
  }
}
