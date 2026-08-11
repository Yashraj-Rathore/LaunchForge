package dev.launchforge.sdk;

import java.util.Objects;

/** Immutable JSON value represented by RFC 8785 canonical JSON. */
public final class JsonValue {
  private final String canonicalJson;

  JsonValue(String canonicalJson) {
    this.canonicalJson = Objects.requireNonNull(canonicalJson, "canonicalJson");
  }

  public static JsonValue parse(String json) {
    return SnapshotParser.parseStandaloneJsonValue(json);
  }

  public String canonicalJson() {
    return canonicalJson;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof JsonValue value && canonicalJson.equals(value.canonicalJson);
  }

  @Override
  public int hashCode() {
    return canonicalJson.hashCode();
  }

  @Override
  public String toString() {
    return canonicalJson;
  }
}
