package dev.launchforge.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.launchforge.sdk.CompiledSnapshot.CompiledAllocation;
import dev.launchforge.sdk.CompiledSnapshot.CompiledFlag;
import dev.launchforge.sdk.CompiledSnapshot.CompiledRollout;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

class EvaluatorTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("matchingOperators")
  void implementsEveryAlgorithmVersionOneOperator(
      String name, String attributeType, String operator, List<JsonNode> values, Object actual) {
    ObjectNodeBuilder snapshot = new ObjectNodeBuilder();
    snapshot.rule(attributeType, operator, values);
    CompiledSnapshot compiled = SnapshotParser.parse(snapshot.build());
    EvaluationContext context = context(actual);

    EvaluationDetail<Boolean> detail =
        Evaluator.evaluateBoolean(compiled, "operator-check", context, false);

    assertTrue(detail.value(), name);
    assertEquals(EvaluationReason.RULE_MATCH, detail.reason());
    assertEquals("rule-1", detail.matchedRuleId().orElseThrow());
  }

  @Test
  void missingWrongTypeAndInvalidSemverNeverMatchNegativeOrOrderedRules() {
    ObjectNodeBuilder notEquals = new ObjectNodeBuilder();
    notEquals.rule("string", "NOT_EQUALS", List.of(json("blocked")));
    CompiledSnapshot snapshot = SnapshotParser.parse(notEquals.build());

    assertFalse(
        Evaluator.evaluateBoolean(
                snapshot, "operator-check", EvaluationContext.builder("subject").build(), false)
            .value());
    assertFalse(
        Evaluator.evaluateBoolean(
                snapshot,
                "operator-check",
                EvaluationContext.builder("subject").attribute("attr", 10).build(),
                false)
            .value());

    ObjectNodeBuilder semver = new ObjectNodeBuilder();
    semver.rule("semver", "SEMVER_GT", List.of(json("1.0.0")));
    EvaluationDetail<Boolean> invalidSemver =
        Evaluator.evaluateBoolean(
            SnapshotParser.parse(semver.build()),
            "operator-check",
            EvaluationContext.builder("subject").attribute("attr", " 2.0.0 ").build(),
            false);
    assertFalse(invalidSemver.value());
    assertEquals(EvaluationReason.DEFAULT_VARIATION, invalidSemver.reason());
  }

  @Test
  void returnsEveryPureEvaluatorReasonAndBoundedFallbackMetadata() {
    var root = SnapshotTestData.root(42);
    var flags = root.withObject("flags");
    flags.set("disabled", SnapshotTestData.booleanFlag(false, false, true));
    flags.set("defaulted", SnapshotTestData.booleanFlag(true, false, true));
    var ruled = SnapshotTestData.booleanFlag(true, false, false);
    SnapshotTestData.addRule(
        ruled,
        "matching-rule",
        SnapshotTestData.condition("plan", "string", "EQUALS", json("pro")),
        "on");
    flags.set("ruled", ruled);
    var rolled = SnapshotTestData.booleanFlag(true, false, false);
    var rollout = rolled.putObject("rollout");
    rollout.put("attribute", "userId");
    rollout.put("salt", "stable_salt_1234");
    rollout.putArray("weights").addObject().put("variation", "on").put("weight", 100_000);
    flags.set("rolled", rolled);
    CompiledSnapshot snapshot = SnapshotParser.parse(SnapshotTestData.canonicalSnapshot(root));
    EvaluationContext context =
        EvaluationContext.builder("subject")
            .attribute("plan", "pro")
            .attribute("userId", "user-1")
            .build();

    assertEquals(
        EvaluationReason.FLAG_NOT_FOUND,
        Evaluator.evaluateBoolean(snapshot, "missing", context, false).reason());
    assertEquals(
        EvaluationReason.TYPE_MISMATCH,
        Evaluator.evaluateString(snapshot, "defaulted", context, "safe").reason());
    assertEquals(
        EvaluationReason.FLAG_DISABLED,
        Evaluator.evaluateBoolean(snapshot, "disabled", context, true).reason());
    assertEquals(
        EvaluationReason.DEFAULT_VARIATION,
        Evaluator.evaluateBoolean(snapshot, "defaulted", context, false).reason());
    assertEquals(
        EvaluationReason.RULE_MATCH,
        Evaluator.evaluateBoolean(snapshot, "ruled", context, false).reason());
    assertEquals(
        EvaluationReason.ROLLOUT_MATCH,
        Evaluator.evaluateBoolean(snapshot, "rolled", context, false).reason());
    assertEquals(
        EvaluationReason.MISSING_ROLLOUT_KEY,
        Evaluator.evaluateBoolean(
                snapshot, "rolled", EvaluationContext.builder("subject").build(), true)
            .reason());

    EvaluationDetail<Boolean> invalid =
        Evaluator.evaluateBoolean(invalidSnapshot(false), "broken", context, true);
    assertEquals(EvaluationReason.INVALID_CONFIG, invalid.reason());
    assertEquals(EvaluationErrorKind.INVALID_CONFIG, invalid.errorKind().orElseThrow());

    EvaluationDetail<Boolean> unexpected =
        Evaluator.evaluateBoolean(invalidSnapshot(true), "broken", context, true);
    assertEquals(EvaluationReason.ERROR_DEFAULT, unexpected.reason());
    assertEquals(EvaluationErrorKind.INTERNAL_ERROR, unexpected.errorKind().orElseThrow());
  }

  @Test
  void concurrentReadersObserveOneImmutableCompiledSnapshot() throws Exception {
    ObjectNodeBuilder builder = new ObjectNodeBuilder();
    builder.rule("string", "EQUALS", List.of(json("pro")));
    CompiledSnapshot snapshot = SnapshotParser.parse(builder.build());
    EvaluationContext context =
        EvaluationContext.builder("subject").attribute("attr", "pro").build();
    List<Callable<Boolean>> tasks =
        IntStream.range(0, 2_000)
            .mapToObj(
                ignored ->
                    (Callable<Boolean>)
                        () ->
                            Evaluator.evaluateBoolean(snapshot, "operator-check", context, false)
                                .value())
            .toList();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (var result : executor.invokeAll(tasks)) {
        assertTrue(result.get());
      }
    }
  }

  @Test
  void firstMatchingRuleWins() {
    var root = SnapshotTestData.root(1);
    var flag = SnapshotTestData.booleanFlag(true, false, true);
    var condition = SnapshotTestData.condition("plan", "string", "EQUALS", json("pro"));
    SnapshotTestData.addRule(flag, "first", condition, "off");
    SnapshotTestData.addRule(flag, "second", condition.deepCopy(), "on");
    root.withObject("flags").set("ordered", flag);
    CompiledSnapshot snapshot = SnapshotParser.parse(SnapshotTestData.canonicalSnapshot(root));

    EvaluationDetail<Boolean> detail =
        Evaluator.evaluateBoolean(
            snapshot,
            "ordered",
            EvaluationContext.builder("subject").attribute("plan", "pro").build(),
            true);

    assertFalse(detail.value());
    assertEquals("first", detail.matchedRuleId().orElseThrow());
  }

  private static EvaluationContext context(Object value) {
    EvaluationContext.Builder builder = EvaluationContext.builder("subject");
    if (value instanceof String text) {
      builder.attribute("attr", text);
    } else if (value instanceof Double number) {
      builder.attribute("attr", number);
    } else if (value instanceof Boolean bool) {
      builder.attribute("attr", bool);
    }
    return builder.build();
  }

  private static CompiledSnapshot invalidSnapshot(boolean throwsUnexpectedly) {
    CompiledRollout rollout =
        throwsUnexpectedly
            ? new CompiledRollout(
                "key", "invalid\nsalt", List.of(new CompiledAllocation("on", 100_000)))
            : null;
    CompiledFlag flag =
        new CompiledFlag(
            FlagType.BOOLEAN,
            throwsUnexpectedly,
            true,
            Map.of("on", true),
            "missing",
            "missing",
            List.of(),
            rollout);
    return new CompiledSnapshot(
        1,
        1,
        "demo-project",
        "test",
        42,
        Instant.parse("2026-08-11T12:00:00Z"),
        "0".repeat(64),
        Map.of("broken", flag));
  }

  private static Stream<Arguments> matchingOperators() {
    return Stream.of(
        Arguments.of("equals", "string", "EQUALS", texts("exact"), "exact"),
        Arguments.of("not equals", "string", "NOT_EQUALS", texts("blocked"), "allowed"),
        Arguments.of("in", "string", "IN", texts("free", "pro"), "pro"),
        Arguments.of("not in", "string", "NOT_IN", texts("blocked", "banned"), "pro"),
        Arguments.of("starts with", "string", "STARTS_WITH", texts("pre"), "prefix"),
        Arguments.of("ends with", "string", "ENDS_WITH", texts("fix"), "prefix"),
        Arguments.of("contains", "string", "CONTAINS", texts("é東"), "Aé東京"),
        Arguments.of("number equals", "number", "EQ", numbers(10), 10.0d),
        Arguments.of("number not equals", "number", "NE", numbers(11), 10.0d),
        Arguments.of("greater", "number", "GT", numbers(9), 10.0d),
        Arguments.of("greater equal", "number", "GTE", numbers(10), 10.0d),
        Arguments.of("less", "number", "LT", numbers(11), 10.0d),
        Arguments.of("less equal", "number", "LTE", numbers(10), 10.0d),
        Arguments.of("between", "number", "BETWEEN_INCLUSIVE", numbers(10, 20), 20.0d),
        Arguments.of("is true", "boolean", "IS_TRUE", List.of(), true),
        Arguments.of("is false", "boolean", "IS_FALSE", List.of(), false),
        Arguments.of("semver equals", "semver", "SEMVER_EQ", texts("1.2.3+one"), "1.2.3+two"),
        Arguments.of("semver greater", "semver", "SEMVER_GT", texts("1.2.3-beta.2"), "1.2.3"),
        Arguments.of("semver greater equal", "semver", "SEMVER_GTE", texts("2.0.0"), "2.0.0"),
        Arguments.of("semver less", "semver", "SEMVER_LT", texts("2.0.0"), "1.9.9"),
        Arguments.of("semver less equal", "semver", "SEMVER_LTE", texts("2.0.0"), "2.0.0"),
        Arguments.of("exists", "string", "EXISTS", List.of(), "anything"),
        Arguments.of("not exists", "string", "NOT_EXISTS", List.of(), null));
  }

  private static List<JsonNode> texts(String... values) {
    return Stream.of(values).map(EvaluatorTest::json).toList();
  }

  private static List<JsonNode> numbers(double... values) {
    return java.util.Arrays.stream(values).mapToObj(EvaluatorTest::json).toList();
  }

  private static JsonNode json(Object value) {
    return SnapshotTestData.MAPPER.valueToTree(value);
  }

  private static final class ObjectNodeBuilder {
    private final tools.jackson.databind.node.ObjectNode root = SnapshotTestData.root(1);
    private final tools.jackson.databind.node.ObjectNode flag =
        SnapshotTestData.booleanFlag(true, false, true);

    private ObjectNodeBuilder() {
      flag.put("defaultVariation", "off");
      root.withObject("flags").set("operator-check", flag);
    }

    void rule(String attributeType, String operator, List<JsonNode> values) {
      SnapshotTestData.addRule(
          flag,
          "rule-1",
          SnapshotTestData.condition(
              "attr", attributeType, operator, values.toArray(JsonNode[]::new)),
          "on");
    }

    String build() {
      return SnapshotTestData.canonicalSnapshot(root);
    }
  }
}
