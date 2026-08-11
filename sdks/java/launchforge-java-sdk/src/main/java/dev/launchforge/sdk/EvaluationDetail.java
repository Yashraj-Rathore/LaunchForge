package dev.launchforge.sdk;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Typed evaluation result with bounded diagnostics and no sensitive context. */
public record EvaluationDetail<T>(
    T value,
    Optional<String> variationId,
    EvaluationReason reason,
    Optional<String> matchedRuleId,
    OptionalLong snapshotRevision,
    OptionalInt rolloutBucket,
    Optional<EvaluationErrorKind> errorKind) {
  public EvaluationDetail {
    Objects.requireNonNull(value, "value");
    variationId = Objects.requireNonNull(variationId, "variationId");
    Objects.requireNonNull(reason, "reason");
    matchedRuleId = Objects.requireNonNull(matchedRuleId, "matchedRuleId");
    snapshotRevision = Objects.requireNonNull(snapshotRevision, "snapshotRevision");
    rolloutBucket = Objects.requireNonNull(rolloutBucket, "rolloutBucket");
    errorKind = Objects.requireNonNull(errorKind, "errorKind");
  }
}
