export type JsonValue =
  null | boolean | number | string | readonly JsonValue[] | { readonly [key: string]: JsonValue };

export type ContextScalar = string | number | boolean;

export interface EvaluationContext {
  readonly key: string;
  readonly attributes: Readonly<Record<string, ContextScalar>>;
}

export type FlagType = 'boolean' | 'string' | 'number' | 'json';

export type EvaluationReason =
  | 'FLAG_NOT_FOUND'
  | 'FLAG_DISABLED'
  | 'DEFAULT_VARIATION'
  | 'RULE_MATCH'
  | 'ROLLOUT_MATCH'
  | 'MISSING_ROLLOUT_KEY'
  | 'TYPE_MISMATCH'
  | 'INVALID_CONFIG'
  | 'SNAPSHOT_UNAVAILABLE'
  | 'ERROR_DEFAULT';

export type EvaluationErrorKind =
  'FLAG_NOT_FOUND' | 'TYPE_MISMATCH' | 'INVALID_CONFIG' | 'SNAPSHOT_UNAVAILABLE' | 'INTERNAL_ERROR';

export interface EvaluationDetail<T> {
  readonly value: T;
  readonly reason: EvaluationReason;
  readonly variationId?: string;
  readonly matchedRuleId?: string;
  readonly snapshotRevision?: number;
  readonly rolloutBucket?: number;
  readonly errorKind?: EvaluationErrorKind;
}

export type AttributeType = 'string' | 'number' | 'boolean' | 'semver';

export type Operator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'IN'
  | 'NOT_IN'
  | 'STARTS_WITH'
  | 'ENDS_WITH'
  | 'CONTAINS'
  | 'EQ'
  | 'NE'
  | 'GT'
  | 'GTE'
  | 'LT'
  | 'LTE'
  | 'BETWEEN_INCLUSIVE'
  | 'IS_TRUE'
  | 'IS_FALSE'
  | 'SEMVER_EQ'
  | 'SEMVER_GT'
  | 'SEMVER_GTE'
  | 'SEMVER_LT'
  | 'SEMVER_LTE'
  | 'EXISTS'
  | 'NOT_EXISTS';

export interface CompiledCondition {
  readonly attribute: string;
  readonly attributeType: AttributeType;
  readonly operator: Operator;
  readonly values: readonly (ContextScalar | SemanticVersion)[];
}

export interface CompiledRule {
  readonly id: string;
  readonly conditions: readonly CompiledCondition[];
  readonly variation: string;
}

export interface CompiledAllocation {
  readonly variation: string;
  readonly upperExclusive: number;
}

export interface CompiledRollout {
  readonly attribute: string;
  readonly salt: string;
  readonly allocations: readonly CompiledAllocation[];
}

export interface CompiledFlag {
  readonly type: FlagType;
  readonly enabled: boolean;
  readonly clientVisible: boolean;
  readonly variations: Readonly<Record<string, boolean | string | number | JsonValue>>;
  readonly offVariation: string;
  readonly defaultVariation: string;
  readonly rules: readonly CompiledRule[];
  readonly rollout?: CompiledRollout;
}

export interface CompiledSnapshot {
  readonly schemaVersion: 1;
  readonly algorithmVersion: 1;
  readonly projectKey: string;
  readonly environmentKey: string;
  readonly revision: number;
  readonly generatedAt: string;
  readonly checksum: string;
  readonly flags: Readonly<Record<string, CompiledFlag>>;
}

export interface SemanticVersion {
  readonly major: bigint;
  readonly minor: bigint;
  readonly patch: bigint;
  readonly prerelease: readonly SemanticIdentifier[];
}

export interface SemanticIdentifier {
  readonly text: string;
  readonly numeric?: bigint;
}
