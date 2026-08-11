package dev.launchforge.sdk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.erdtman.jcs.JsonCanonicalizer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class SnapshotTestData {
  static final ObjectMapper MAPPER = new ObjectMapper();

  private SnapshotTestData() {}

  static ObjectNode root(long revision) {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("schemaVersion", 1);
    root.put("algorithmVersion", 1);
    root.put("projectKey", "demo-project");
    root.put("environmentKey", "test");
    root.put("revision", revision);
    root.put("generatedAt", "2026-08-11T12:00:00Z");
    root.set("flags", MAPPER.createObjectNode());
    return root;
  }

  static ObjectNode booleanFlag(boolean enabled, boolean offValue, boolean defaultValue) {
    ObjectNode flag = MAPPER.createObjectNode();
    flag.put("type", "boolean");
    flag.put("enabled", enabled);
    flag.put("clientVisible", true);
    ArrayNode variations = flag.putArray("variations");
    variations.addObject().put("id", "off").put("value", offValue);
    variations.addObject().put("id", "on").put("value", defaultValue);
    flag.put("offVariation", "off");
    flag.put("defaultVariation", "on");
    flag.putArray("rules");
    return flag;
  }

  static String canonicalSnapshot(ObjectNode root) {
    try {
      root.remove("checksum");
      String withoutChecksum = canonical(MAPPER.writeValueAsString(root));
      root.put("checksum", sha256(withoutChecksum));
      return canonical(MAPPER.writeValueAsString(root));
    } catch (JacksonException exception) {
      throw new IllegalStateException(exception);
    }
  }

  static ObjectNode copyOf(String snapshot) {
    try {
      return (ObjectNode) MAPPER.readTree(snapshot);
    } catch (JacksonException exception) {
      throw new IllegalStateException(exception);
    }
  }

  static String withRecomputedChecksum(ObjectNode root) {
    return canonicalSnapshot(root);
  }

  static ObjectNode condition(
      String attribute, String attributeType, String operator, JsonNode... values) {
    ObjectNode condition = MAPPER.createObjectNode();
    condition.put("attribute", attribute);
    condition.put("attributeType", attributeType);
    condition.put("operator", operator);
    ArrayNode array = condition.putArray("values");
    for (JsonNode value : values) {
      array.add(value);
    }
    return condition;
  }

  static void addRule(ObjectNode flag, String id, ObjectNode condition, String variation) {
    ObjectNode rule = ((ArrayNode) flag.get("rules")).addObject();
    rule.put("id", id);
    rule.putArray("conditions").add(condition);
    rule.put("variation", variation);
  }

  private static String canonical(String json) {
    try {
      return new JsonCanonicalizer(json).getEncodedString();
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
