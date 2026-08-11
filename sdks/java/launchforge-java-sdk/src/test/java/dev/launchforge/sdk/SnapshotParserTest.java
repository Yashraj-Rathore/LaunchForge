package dev.launchforge.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class SnapshotParserTest {
  @Test
  void validatesChecksumVersionsAndTypedValuesBeforeCompilation() {
    ObjectNode root = SnapshotTestData.root(7);
    root.withObject("flags").set("release", SnapshotTestData.booleanFlag(true, false, true));
    String snapshot = SnapshotTestData.canonicalSnapshot(root);

    CompiledSnapshot parsed = SnapshotParser.parse(snapshot);

    assertEquals(7, parsed.revision());
    assertEquals("demo-project", parsed.projectKey());
    assertEquals("test", parsed.environmentKey());
    assertThrows(SnapshotValidationException.class, () -> SnapshotParser.parse(snapshot + "{}"));

    ObjectNode badChecksum = SnapshotTestData.copyOf(snapshot);
    badChecksum.put("revision", 8);
    assertThrows(
        SnapshotValidationException.class, () -> SnapshotParser.parse(badChecksum.toString()));

    ObjectNode badVersion = SnapshotTestData.copyOf(snapshot);
    badVersion.put("algorithmVersion", 2);
    assertThrows(
        SnapshotValidationException.class,
        () -> SnapshotParser.parse(SnapshotTestData.withRecomputedChecksum(badVersion)));
  }

  @Test
  void rejectsDuplicateFieldsUnsafeIntegerSpellingAndMalformedRollout() {
    String duplicate =
        "{\"schemaVersion\":1,\"schemaVersion\":1,\"algorithmVersion\":1,"
            + "\"projectKey\":\"demo-project\",\"environmentKey\":\"test\","
            + "\"revision\":1,\"generatedAt\":\"2026-08-11T12:00:00Z\","
            + "\"flags\":{},\"checksum\":\""
            + "0".repeat(64)
            + "\"}";
    assertThrows(SnapshotValidationException.class, () -> SnapshotParser.parse(duplicate));
    assertThrows(
        SnapshotValidationException.class,
        () -> JsonValue.parse("{\"first\":true}{\"second\":false}"));
    assertThrows(
        SnapshotValidationException.class,
        () -> JsonValue.parse("{\"unsafe\":9007199254740992.0}"));

    ObjectNode root = SnapshotTestData.root(1);
    ObjectNode flag = SnapshotTestData.booleanFlag(true, false, true);
    ObjectNode rollout = flag.putObject("rollout");
    rollout.put("attribute", "key");
    rollout.put("salt", "stable_salt_1234");
    rollout.putArray("weights").addObject().put("variation", "on").put("weight", 99_999);
    root.withObject("flags").set("release", flag);
    assertThrows(
        SnapshotValidationException.class,
        () -> SnapshotParser.parse(SnapshotTestData.canonicalSnapshot(root)));
  }

  @Test
  void canonicalJsonValuesAreImmutableByRepresentation() {
    JsonValue first = JsonValue.parse("{\"b\":1.0,\"a\":[true,null]}");
    JsonValue second = JsonValue.parse("{\"a\":[true,null],\"b\":1}");

    assertEquals("{\"a\":[true,null],\"b\":1}", first.canonicalJson());
    assertEquals(first, second);
  }
}
