import { matchesCondition } from './condition.js';
import { rolloutBucket } from './rollout.js';
import type {
  CompiledFlag,
  CompiledSnapshot,
  EvaluationContext,
  EvaluationDetail,
  FlagType,
  JsonValue,
} from './types.js';

export function evaluateBoolean(
  snapshot: CompiledSnapshot | null,
  flagKey: string,
  context: EvaluationContext,
  defaultValue: boolean,
): EvaluationDetail<boolean> {
  return evaluate(snapshot, flagKey, context, defaultValue, 'boolean');
}

export function evaluateString(
  snapshot: CompiledSnapshot | null,
  flagKey: string,
  context: EvaluationContext,
  defaultValue: string,
): EvaluationDetail<string> {
  return evaluate(snapshot, flagKey, context, defaultValue, 'string');
}

export function evaluateNumber(
  snapshot: CompiledSnapshot | null,
  flagKey: string,
  context: EvaluationContext,
  defaultValue: number,
): EvaluationDetail<number> {
  if (!Number.isFinite(defaultValue)) {
    throw new Error('Default number must be finite binary64');
  }
  return evaluate(
    snapshot,
    flagKey,
    context,
    Object.is(defaultValue, -0) ? 0 : defaultValue,
    'number',
  );
}

export function evaluateJson<T extends JsonValue>(
  snapshot: CompiledSnapshot | null,
  flagKey: string,
  context: EvaluationContext,
  defaultValue: T,
): EvaluationDetail<JsonValue | T> {
  return evaluate(snapshot, flagKey, context, defaultValue, 'json');
}

function evaluate<T>(
  snapshot: CompiledSnapshot | null,
  flagKey: string,
  context: EvaluationContext,
  defaultValue: T,
  requestedType: FlagType,
): EvaluationDetail<T> {
  if (snapshot === null) {
    return fallback(defaultValue, 'SNAPSHOT_UNAVAILABLE', 'SNAPSHOT_UNAVAILABLE');
  }
  try {
    const flag = snapshot.flags[flagKey];
    if (flag === undefined) {
      return fallback(defaultValue, 'FLAG_NOT_FOUND', 'FLAG_NOT_FOUND', snapshot.revision);
    }
    if (flag.type !== requestedType) {
      return fallback(defaultValue, 'TYPE_MISMATCH', 'TYPE_MISMATCH', snapshot.revision);
    }
    if (!flag.enabled) {
      return variation(flag, flag.offVariation, 'FLAG_DISABLED', defaultValue, snapshot.revision);
    }
    for (const rule of flag.rules) {
      if (rule.conditions.every((condition) => matchesCondition(condition, context))) {
        return variation(flag, rule.variation, 'RULE_MATCH', defaultValue, snapshot.revision, {
          matchedRuleId: rule.id,
        });
      }
    }
    if (flag.rollout !== undefined) {
      const rawSubject =
        flag.rollout.attribute === 'key' ? context.key : context.attributes[flag.rollout.attribute];
      if (typeof rawSubject !== 'string' || rawSubject.length === 0) {
        return variation(
          flag,
          flag.defaultVariation,
          'MISSING_ROLLOUT_KEY',
          defaultValue,
          snapshot.revision,
        );
      }
      const bucket = rolloutBucket(flagKey, flag.rollout.salt, rawSubject);
      for (const allocation of flag.rollout.allocations) {
        if (bucket < allocation.upperExclusive) {
          return variation(
            flag,
            allocation.variation,
            'ROLLOUT_MATCH',
            defaultValue,
            snapshot.revision,
            { rolloutBucket: bucket },
          );
        }
      }
      return fallback(defaultValue, 'INVALID_CONFIG', 'INVALID_CONFIG', snapshot.revision);
    }
    return variation(
      flag,
      flag.defaultVariation,
      'DEFAULT_VARIATION',
      defaultValue,
      snapshot.revision,
    );
  } catch {
    return fallback(defaultValue, 'ERROR_DEFAULT', 'INTERNAL_ERROR', snapshot.revision);
  }
}

function variation<T>(
  flag: CompiledFlag,
  variationId: string,
  reason: EvaluationDetail<T>['reason'],
  defaultValue: T,
  revision: number,
  metadata: { readonly matchedRuleId?: string; readonly rolloutBucket?: number } = {},
): EvaluationDetail<T> {
  const value = flag.variations[variationId];
  if (value === undefined) {
    return fallback(defaultValue, 'INVALID_CONFIG', 'INVALID_CONFIG', revision);
  }
  return {
    value: value as T,
    variationId,
    reason,
    snapshotRevision: revision,
    ...metadata,
  };
}

function fallback<T>(
  value: T,
  reason: EvaluationDetail<T>['reason'],
  errorKind: NonNullable<EvaluationDetail<T>['errorKind']>,
  snapshotRevision?: number,
): EvaluationDetail<T> {
  return {
    value,
    reason,
    errorKind,
    ...(snapshotRevision === undefined ? {} : { snapshotRevision }),
  };
}
