package dev.launchforge.performance;

import dev.launchforge.sdk.CompiledSnapshot;
import dev.launchforge.sdk.EvaluationContext;
import dev.launchforge.sdk.EvaluationDetail;
import dev.launchforge.sdk.Evaluator;
import dev.launchforge.sdk.JsonValue;
import dev.launchforge.sdk.SnapshotParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import org.erdtman.jcs.JsonCanonicalizer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class EvaluatorBenchmark {
  @Benchmark
  public EvaluationDetail<Boolean> booleanDefault(BenchmarkState state) {
    return Evaluator.evaluateBoolean(state.smallSnapshot, "boolean-default", state.basic, false);
  }

  @Benchmark
  public EvaluationDetail<Boolean> ruleMatch(BenchmarkState state) {
    return Evaluator.evaluateBoolean(state.smallSnapshot, "rule-flag", state.ruleMatch, false);
  }

  @Benchmark
  public EvaluationDetail<Boolean> hundredthRuleMatch(BenchmarkState state) {
    return Evaluator.evaluateBoolean(state.largeRuleSnapshot, "rule-flag", state.lastRule, false);
  }

  @Benchmark
  public EvaluationDetail<Boolean> percentageRollout(BenchmarkState state) {
    return Evaluator.evaluateBoolean(state.smallSnapshot, "rollout-flag", state.rollout, false);
  }

  @Benchmark
  public EvaluationDetail<JsonValue> jsonVariation(BenchmarkState state) {
    return Evaluator.evaluateJson(
        state.smallSnapshot, "json-default", state.basic, state.jsonDefault);
  }

  @State(Scope.Benchmark)
  public static class BenchmarkState {
    private final ObjectMapper mapper = new ObjectMapper();
    CompiledSnapshot smallSnapshot;
    CompiledSnapshot largeRuleSnapshot;
    EvaluationContext basic;
    EvaluationContext ruleMatch;
    EvaluationContext lastRule;
    EvaluationContext rollout;
    JsonValue jsonDefault;

    @Setup
    public void setup() throws Exception {
      try (InputStream input =
          EvaluatorBenchmark.class.getClassLoader().getResourceAsStream("evaluator-v1.json")) {
        if (input == null) {
          throw new IllegalStateException("Golden evaluator corpus is missing");
        }
        JsonNode corpus = mapper.readTree(input);
        ObjectNode snapshot = ((ObjectNode) corpus.get("evaluationSnapshot")).deepCopy();
        smallSnapshot = SnapshotParser.parse(mapper.writeValueAsString(snapshot));
        largeRuleSnapshot = SnapshotParser.parse(largeRuleSnapshot(snapshot));
      }
      basic = EvaluationContext.builder("benchmark-subject").build();
      ruleMatch = EvaluationContext.builder("benchmark-subject").attribute("name", "Zoë").build();
      lastRule =
          EvaluationContext.builder("benchmark-subject").attribute("name", "target-99").build();
      rollout =
          EvaluationContext.builder("benchmark-subject")
              .attribute("userId", "benchmark-user-42")
              .build();
      jsonDefault = JsonValue.parse("{}");
    }

    private String largeRuleSnapshot(ObjectNode source) throws Exception {
      ObjectNode snapshot = source.deepCopy();
      ObjectNode flag = (ObjectNode) snapshot.get("flags").get("rule-flag");
      ArrayNode rules = mapper.createArrayNode();
      for (int index = 0; index < SnapshotParser.MAX_RULES; index++) {
        ObjectNode rule = rules.addObject();
        rule.put("id", "rule-" + index);
        rule.put("variation", "on");
        ObjectNode condition = rule.putArray("conditions").addObject();
        condition.put("attribute", "name");
        condition.put("attributeType", "string");
        condition.put("operator", "EQUALS");
        condition.putArray("values").add("target-" + index);
      }
      flag.set("rules", rules);
      snapshot.remove("checksum");
      byte[] canonical =
          new JsonCanonicalizer(mapper.writeValueAsString(snapshot)).getEncodedUTF8();
      snapshot.put(
          "checksum",
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical)));
      return new String(
          new JsonCanonicalizer(mapper.writeValueAsString(snapshot)).getEncodedUTF8(),
          StandardCharsets.UTF_8);
    }
  }
}
