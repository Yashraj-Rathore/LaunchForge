package dev.launchforge.sdk;

/** Safe, bounded fallback metadata; stack traces and raw context are never exposed. */
public enum EvaluationErrorKind {
  FLAG_NOT_FOUND,
  TYPE_MISMATCH,
  INVALID_CONFIG,
  SNAPSHOT_UNAVAILABLE,
  INTERNAL_ERROR
}
