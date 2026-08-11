package dev.launchforge.sdk;

/** Bounded algorithm-version-1 evaluation reasons. */
public enum EvaluationReason {
  FLAG_NOT_FOUND,
  FLAG_DISABLED,
  DEFAULT_VARIATION,
  RULE_MATCH,
  ROLLOUT_MATCH,
  MISSING_ROLLOUT_KEY,
  TYPE_MISMATCH,
  INVALID_CONFIG,
  SNAPSHOT_UNAVAILABLE,
  ERROR_DEFAULT
}
