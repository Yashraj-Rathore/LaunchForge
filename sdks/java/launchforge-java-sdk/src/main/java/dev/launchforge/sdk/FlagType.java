package dev.launchforge.sdk;

/** Runtime flag types supported by snapshot schema and algorithm version 1. */
public enum FlagType {
  BOOLEAN,
  STRING,
  NUMBER,
  JSON;

  static FlagType fromWireValue(String value) {
    return switch (value) {
      case "boolean" -> BOOLEAN;
      case "string" -> STRING;
      case "number" -> NUMBER;
      case "json" -> JSON;
      case null, default -> throw new SnapshotValidationException("Flag type is unsupported");
    };
  }
}
