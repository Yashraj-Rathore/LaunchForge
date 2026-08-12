export { canonicalize } from './canonical-json.js';
export { createEvaluationContext } from './context.js';
export { evaluateBoolean, evaluateJson, evaluateNumber, evaluateString } from './evaluator.js';
export { BUCKET_COUNT, rolloutBucket } from './rollout.js';
export {
  EVALUATION_ALGORITHM_VERSION,
  MAX_SNAPSHOT_BYTES,
  SNAPSHOT_SCHEMA_VERSION,
  SnapshotValidationError,
  parseSnapshot,
  snapshotChecksum,
} from './snapshot.js';
export { sha256Hex } from './sha256.js';
export { parseStrictJson } from './strict-json.js';
export const EVALUATION_REASONS = [
  'FLAG_NOT_FOUND',
  'FLAG_DISABLED',
  'DEFAULT_VARIATION',
  'RULE_MATCH',
  'ROLLOUT_MATCH',
  'MISSING_ROLLOUT_KEY',
  'TYPE_MISMATCH',
  'INVALID_CONFIG',
  'SNAPSHOT_UNAVAILABLE',
  'ERROR_DEFAULT',
] as const;
export type {
  CompiledSnapshot,
  ContextScalar,
  EvaluationContext,
  EvaluationDetail,
  EvaluationErrorKind,
  EvaluationReason,
  FlagType,
  JsonValue,
} from './types.js';
