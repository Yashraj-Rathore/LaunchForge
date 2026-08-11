package dev.launchforge.sdk;

import dev.launchforge.sdk.CompiledSnapshot.CompiledAllocation;
import dev.launchforge.sdk.CompiledSnapshot.CompiledCondition;
import dev.launchforge.sdk.CompiledSnapshot.CompiledFlag;
import dev.launchforge.sdk.CompiledSnapshot.CompiledRollout;
import dev.launchforge.sdk.CompiledSnapshot.CompiledRule;
import dev.launchforge.sdk.CompiledSnapshot.Operator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Pure, deterministic algorithm-version-1 evaluator. Evaluation performs no I/O or locking. */
public final class Evaluator {
  private Evaluator() {}

  public static EvaluationDetail<Boolean> evaluateBoolean(
      CompiledSnapshot snapshot, String flagKey, EvaluationContext context, boolean defaultValue) {
    return evaluate(snapshot, flagKey, context, defaultValue, FlagType.BOOLEAN);
  }

  public static EvaluationDetail<String> evaluateString(
      CompiledSnapshot snapshot, String flagKey, EvaluationContext context, String defaultValue) {
    return evaluate(snapshot, flagKey, context, defaultValue, FlagType.STRING);
  }

  public static EvaluationDetail<Double> evaluateNumber(
      CompiledSnapshot snapshot, String flagKey, EvaluationContext context, double defaultValue) {
    if (!Double.isFinite(defaultValue)) {
      throw new IllegalArgumentException("Default number must be finite binary64");
    }
    return evaluate(
        snapshot, flagKey, context, defaultValue == 0.0d ? 0.0d : defaultValue, FlagType.NUMBER);
  }

  public static EvaluationDetail<JsonValue> evaluateJson(
      CompiledSnapshot snapshot,
      String flagKey,
      EvaluationContext context,
      JsonValue defaultValue) {
    return evaluate(snapshot, flagKey, context, defaultValue, FlagType.JSON);
  }

  private static <T> EvaluationDetail<T> evaluate(
      CompiledSnapshot snapshot,
      String flagKey,
      EvaluationContext context,
      T defaultValue,
      FlagType requestedType) {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(flagKey, "flagKey");
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(defaultValue, "defaultValue");
    try {
      CompiledFlag flag = snapshot.flag(flagKey);
      if (flag == null) {
        return fallback(
            defaultValue,
            EvaluationReason.FLAG_NOT_FOUND,
            EvaluationErrorKind.FLAG_NOT_FOUND,
            snapshot.revision());
      }
      if (flag.type() != requestedType) {
        return fallback(
            defaultValue,
            EvaluationReason.TYPE_MISMATCH,
            EvaluationErrorKind.TYPE_MISMATCH,
            snapshot.revision());
      }
      if (!flag.enabled()) {
        return variation(
            flag,
            flag.offVariation(),
            EvaluationReason.FLAG_DISABLED,
            null,
            -1,
            defaultValue,
            snapshot.revision());
      }
      for (CompiledRule rule : flag.rules()) {
        if (matches(rule.conditions(), context)) {
          return variation(
              flag,
              rule.variation(),
              EvaluationReason.RULE_MATCH,
              rule.id(),
              -1,
              defaultValue,
              snapshot.revision());
        }
      }
      CompiledRollout rollout = flag.rollout();
      if (rollout != null) {
        Object rawSubject = context.value(rollout.attribute());
        if (!(rawSubject instanceof String subject) || subject.isEmpty()) {
          return variation(
              flag,
              flag.defaultVariation(),
              EvaluationReason.MISSING_ROLLOUT_KEY,
              null,
              -1,
              defaultValue,
              snapshot.revision());
        }
        int bucket = RolloutHasher.bucket(flagKey, rollout.salt(), subject);
        for (CompiledAllocation allocation : rollout.allocations()) {
          if (bucket < allocation.upperExclusive()) {
            return variation(
                flag,
                allocation.variation(),
                EvaluationReason.ROLLOUT_MATCH,
                null,
                bucket,
                defaultValue,
                snapshot.revision());
          }
        }
        return fallback(
            defaultValue,
            EvaluationReason.INVALID_CONFIG,
            EvaluationErrorKind.INVALID_CONFIG,
            snapshot.revision());
      }
      return variation(
          flag,
          flag.defaultVariation(),
          EvaluationReason.DEFAULT_VARIATION,
          null,
          -1,
          defaultValue,
          snapshot.revision());
    } catch (RuntimeException exception) {
      return fallback(
          defaultValue,
          EvaluationReason.ERROR_DEFAULT,
          EvaluationErrorKind.INTERNAL_ERROR,
          snapshot.revision());
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> EvaluationDetail<T> variation(
      CompiledFlag flag,
      String variationId,
      EvaluationReason reason,
      String ruleId,
      int bucket,
      T defaultValue,
      long revision) {
    Object value = flag.variations().get(variationId);
    if (value == null) {
      return fallback(
          defaultValue,
          reason == EvaluationReason.ERROR_DEFAULT ? reason : EvaluationReason.INVALID_CONFIG,
          EvaluationErrorKind.INVALID_CONFIG,
          revision);
    }
    return new EvaluationDetail<>(
        (T) value,
        Optional.of(variationId),
        reason,
        Optional.ofNullable(ruleId),
        OptionalLong.of(revision),
        bucket >= 0 ? OptionalInt.of(bucket) : OptionalInt.empty(),
        Optional.empty());
  }

  private static <T> EvaluationDetail<T> fallback(
      T defaultValue, EvaluationReason reason, EvaluationErrorKind errorKind, long revision) {
    return new EvaluationDetail<>(
        defaultValue,
        Optional.empty(),
        reason,
        Optional.empty(),
        OptionalLong.of(revision),
        OptionalInt.empty(),
        Optional.of(errorKind));
  }

  private static boolean matches(List<CompiledCondition> conditions, EvaluationContext context) {
    for (CompiledCondition condition : conditions) {
      if (!matches(condition, context)) {
        return false;
      }
    }
    return true;
  }

  private static boolean matches(CompiledCondition condition, EvaluationContext context) {
    Object actual = context.value(condition.attribute());
    Operator operator = condition.operator();
    if (operator == Operator.EXISTS) {
      return actual != null;
    }
    if (operator == Operator.NOT_EXISTS) {
      return actual == null;
    }
    if (actual == null || !runtimeTypeMatches(condition, actual)) {
      return false;
    }
    List<Object> values = condition.values();
    return switch (operator) {
      case EQUALS -> actual.equals(values.getFirst());
      case NOT_EQUALS -> !actual.equals(values.getFirst());
      case IN -> values.contains(actual);
      case NOT_IN -> !values.contains(actual);
      case STARTS_WITH -> ((String) actual).startsWith((String) values.getFirst());
      case ENDS_WITH -> ((String) actual).endsWith((String) values.getFirst());
      case CONTAINS -> ((String) actual).contains((String) values.getFirst());
      case EQ -> compareNumber(actual, values.getFirst()) == 0;
      case NE -> compareNumber(actual, values.getFirst()) != 0;
      case GT -> compareNumber(actual, values.getFirst()) > 0;
      case GTE -> compareNumber(actual, values.getFirst()) >= 0;
      case LT -> compareNumber(actual, values.getFirst()) < 0;
      case LTE -> compareNumber(actual, values.getFirst()) <= 0;
      case BETWEEN_INCLUSIVE ->
          compareNumber(actual, values.get(0)) >= 0 && compareNumber(actual, values.get(1)) <= 0;
      case IS_TRUE -> Boolean.TRUE.equals(actual);
      case IS_FALSE -> Boolean.FALSE.equals(actual);
      case SEMVER_EQ -> semVerMatches(actual, values.getFirst(), comparison -> comparison == 0);
      case SEMVER_GT -> semVerMatches(actual, values.getFirst(), comparison -> comparison > 0);
      case SEMVER_GTE -> semVerMatches(actual, values.getFirst(), comparison -> comparison >= 0);
      case SEMVER_LT -> semVerMatches(actual, values.getFirst(), comparison -> comparison < 0);
      case SEMVER_LTE -> semVerMatches(actual, values.getFirst(), comparison -> comparison <= 0);
      case EXISTS, NOT_EXISTS ->
          throw new IllegalStateException("Existence operator already handled");
    };
  }

  private static boolean runtimeTypeMatches(CompiledCondition condition, Object actual) {
    return switch (condition.attributeType()) {
      case STRING, SEMVER -> actual instanceof String;
      case NUMBER -> actual instanceof Double;
      case BOOLEAN -> actual instanceof Boolean;
    };
  }

  private static int compareNumber(Object left, Object right) {
    return Double.compare((Double) left, (Double) right);
  }

  private static boolean semVerMatches(
      Object left, Object right, java.util.function.IntPredicate predicate) {
    SemanticVersion actual = SemanticVersion.parseOrNull(left);
    return actual != null && predicate.test(actual.compareTo((SemanticVersion) right));
  }
}
