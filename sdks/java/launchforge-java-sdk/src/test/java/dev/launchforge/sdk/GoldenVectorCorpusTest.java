package dev.launchforge.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.erdtman.jcs.JsonCanonicalizer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class GoldenVectorCorpusTest {
  private static final String RESOURCE = "/evaluator-v1.json";

  @Test
  void frozenCorpusExactlyMatchesReferenceGenerator() throws Exception {
    String generated = GoldenVectorCorpus.generate();
    if (Boolean.getBoolean("launchforge.updateGoldenVectors")) {
      Path target = repositoryRoot().resolve("contracts/golden-vectors/evaluator-v1.json");
      Files.createDirectories(target.getParent());
      Files.writeString(target, generated, StandardCharsets.UTF_8);
      return;
    }
    try (InputStream input = GoldenVectorCorpusTest.class.getResourceAsStream(RESOURCE)) {
      if (input == null) {
        throw new AssertionError("Golden corpus is absent; run its documented generator command");
      }
      assertEquals(generated, new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @Test
  void rolloutVectorsMatchProductionHashingAndCorpusChecksum() throws Exception {
    ObjectNode corpus = generatedCorpus();
    String expectedChecksum = corpus.remove("corpusChecksum").stringValue();
    String canonical = new JsonCanonicalizer(corpus.toString()).getEncodedString();
    assertEquals(expectedChecksum, sha256(canonical));
    assertEquals(
        Set.of(EvaluationReason.values()).stream().map(Enum::name).collect(Collectors.toSet()),
        corpus
            .withArray("reasonCodes")
            .valueStream()
            .map(JsonNode::stringValue)
            .collect(Collectors.toSet()));

    for (JsonNode vector : corpus.withArray("rolloutVectors")) {
      if (vector.has("sampleSize")) {
        int selected = 0;
        for (int index = 0; index < vector.path("sampleSize").intValue(); index++) {
          int bucket =
              RolloutHasher.bucket(
                  vector.path("flagKey").stringValue(),
                  vector.path("salt").stringValue(),
                  vector.path("subjectPrefix").stringValue() + index);
          if (bucket < vector.path("thresholdExclusive").intValue()) {
            selected++;
          }
        }
        assertEquals(vector.path("selectedCount").intValue(), selected);
        assertTrue(selected >= 900 && selected <= 1_100, "10% sample is approximate");
      } else {
        assertEquals(
            vector.path("bucket").intValue(),
            RolloutHasher.bucket(
                vector.path("flagKey").stringValue(),
                vector.path("salt").stringValue(),
                vector.path("subject").stringValue()),
            vector.path("name").stringValue());
      }
    }
  }

  @Test
  void languageNeutralOperatorCasesDriveTheEvaluator() throws Exception {
    ObjectNode corpus = generatedCorpus();
    for (JsonNode item : corpus.withArray("operatorCases")) {
      ObjectNode root = SnapshotTestData.root(1);
      ObjectNode flag = SnapshotTestData.booleanFlag(true, false, true);
      flag.put("defaultVariation", "off");
      ObjectNode condition = SnapshotTestData.MAPPER.createObjectNode();
      condition.put("attribute", "attr");
      condition.put("attributeType", item.path("attributeType").stringValue());
      condition.put("operator", item.path("operator").stringValue());
      condition.set("values", item.path("configuredValues"));
      SnapshotTestData.addRule(flag, "rule-1", condition, "on");
      root.withObject("flags").set("operator-check", flag);
      CompiledSnapshot snapshot = SnapshotParser.parse(SnapshotTestData.canonicalSnapshot(root));
      EvaluationContext.Builder context = EvaluationContext.builder("subject");
      if (item.path("contextPresent").booleanValue()) {
        addAttribute(context, item.path("contextValue"));
      }

      boolean actual =
          Evaluator.evaluateBoolean(snapshot, "operator-check", context.build(), false).value();

      assertEquals(
          item.path("expectedMatch").booleanValue(), actual, item.path("name").stringValue());
    }
  }

  @Test
  void languageNeutralEvaluationAndRejectionCasesExecute() throws Exception {
    ObjectNode corpus = generatedCorpus();
    CompiledSnapshot snapshot = SnapshotParser.parse(corpus.path("evaluationSnapshot").toString());
    for (JsonNode item : corpus.withArray("evaluationCases")) {
      EvaluationContext.Builder context =
          EvaluationContext.builder(item.path("contextKey").stringValue());
      item.path("attributes")
          .properties()
          .forEach(entry -> addAttribute(context, entry.getKey(), entry.getValue()));
      EvaluationDetail<?> detail = evaluate(snapshot, item, context.build());
      JsonNode expected = item.path("expected");

      assertEquals(
          expected.path("reason").stringValue(),
          detail.reason().name(),
          item.path("name").stringValue());
      assertEquals(expected.path("revision").longValue(), detail.snapshotRevision().orElseThrow());
      assertEquals(optionalText(expected, "variation"), detail.variationId());
      assertEquals(optionalText(expected, "rule"), detail.matchedRuleId());
      assertValue(expected.path("value"), detail.value(), item.path("requestedType").stringValue());
    }

    for (JsonNode malformed : corpus.withArray("malformedSnapshots")) {
      assertThrows(
          SnapshotValidationException.class,
          () -> SnapshotParser.parse(malformed.path("snapshot").stringValue()),
          malformed.path("name").stringValue());
    }
  }

  @Test
  void languageNeutralJsonVariationSizeBoundariesUseCanonicalUtf8Bytes() throws Exception {
    JsonNode boundaries =
        SnapshotTestData.MAPPER.readTree(
            Files.readString(
                repositoryRoot().resolve("contracts/golden-vectors/json-variation-size-v1.json"),
                StandardCharsets.UTF_8));
    int maximum = boundaries.path("maximumCanonicalUtf8Bytes").intValue();
    String codePoint = boundaries.path("codePoint").stringValue();
    String accepted = codePoint.repeat(boundaries.path("accepted").path("repeatCount").intValue());
    String rejected = codePoint.repeat(boundaries.path("rejected").path("repeatCount").intValue());

    assertEquals(
        boundaries.path("accepted").path("canonicalUtf8Bytes").intValue(),
        SnapshotTestData.MAPPER
            .writeValueAsString(accepted)
            .getBytes(StandardCharsets.UTF_8)
            .length);
    assertEquals(
        boundaries.path("rejected").path("canonicalUtf8Bytes").intValue(),
        SnapshotTestData.MAPPER
            .writeValueAsString(rejected)
            .getBytes(StandardCharsets.UTF_8)
            .length);
    assertEquals(maximum, SnapshotParser.MAX_JSON_VALUE_BYTES);
    SnapshotParser.parse(jsonVariationSnapshot(accepted));
    assertThrows(
        SnapshotValidationException.class,
        () -> SnapshotParser.parse(jsonVariationSnapshot(rejected)));
  }

  private static EvaluationDetail<?> evaluate(
      CompiledSnapshot snapshot, JsonNode item, EvaluationContext context) {
    String flagKey = item.path("flagKey").stringValue();
    JsonNode fallback = item.path("defaultValue");
    return switch (item.path("requestedType").stringValue()) {
      case "boolean" ->
          Evaluator.evaluateBoolean(snapshot, flagKey, context, fallback.booleanValue());
      case "string" -> Evaluator.evaluateString(snapshot, flagKey, context, fallback.stringValue());
      case "number" -> Evaluator.evaluateNumber(snapshot, flagKey, context, fallback.doubleValue());
      case "json" ->
          Evaluator.evaluateJson(snapshot, flagKey, context, JsonValue.parse(fallback.toString()));
      default -> throw new IllegalArgumentException("Unknown requested type in golden corpus");
    };
  }

  private static void assertValue(JsonNode expected, Object actual, String requestedType) {
    if ("json".equals(requestedType)) {
      assertEquals(JsonValue.parse(expected.toString()), actual);
    } else if ("number".equals(requestedType)) {
      assertEquals(expected.doubleValue(), actual);
    } else if ("boolean".equals(requestedType)) {
      assertEquals(expected.booleanValue(), actual);
    } else {
      assertEquals(expected.stringValue(), actual);
    }
  }

  private static void addAttribute(EvaluationContext.Builder context, JsonNode value) {
    addAttribute(context, "attr", value);
  }

  private static void addAttribute(EvaluationContext.Builder context, String name, JsonNode value) {
    if (value.isString()) {
      context.attribute(name, value.stringValue());
    } else if (value.isNumber()) {
      context.attribute(name, value.doubleValue());
    } else if (value.isBoolean()) {
      context.attribute(name, value.booleanValue());
    } else {
      throw new IllegalArgumentException("Golden context value must be scalar");
    }
  }

  private static Optional<String> optionalText(JsonNode node, String field) {
    return node.has(field) ? Optional.of(node.path(field).stringValue()) : Optional.empty();
  }

  private static ObjectNode generatedCorpus() throws Exception {
    return (ObjectNode) SnapshotTestData.MAPPER.readTree(GoldenVectorCorpus.generate());
  }

  private static String sha256(String value) throws Exception {
    return java.util.HexFormat.of()
        .formatHex(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static String jsonVariationSnapshot(String boundaryValue) {
    ObjectNode root = SnapshotTestData.root(1);
    ObjectNode flag = SnapshotTestData.MAPPER.createObjectNode();
    flag.put("type", "json");
    flag.put("enabled", true);
    flag.put("clientVisible", true);
    var variations = flag.putArray("variations");
    variations.addObject().put("id", "boundary").put("value", boundaryValue);
    ObjectNode fallback = variations.addObject();
    fallback.put("id", "fallback");
    fallback.set("value", SnapshotTestData.MAPPER.createObjectNode());
    flag.put("offVariation", "fallback");
    flag.put("defaultVariation", "boundary");
    flag.putArray("rules");
    root.withObject("flags").set("json-boundary", flag);
    return SnapshotTestData.canonicalSnapshot(root);
  }

  private static Path repositoryRoot() throws IOException {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("pom.xml"))
          && Files.isDirectory(current.resolve("docs"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IOException("Repository root not found");
  }
}
