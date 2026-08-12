import { compareSemanticVersions, parseSemanticVersion } from './semver.js';
import type {
  CompiledCondition,
  ContextScalar,
  EvaluationContext,
  SemanticVersion,
} from './types.js';

export function matchesCondition(
  condition: CompiledCondition,
  context: EvaluationContext,
): boolean {
  const actual: ContextScalar | undefined =
    condition.attribute === 'key' ? context.key : context.attributes[condition.attribute];
  if (condition.operator === 'EXISTS') {
    return actual !== undefined;
  }
  if (condition.operator === 'NOT_EXISTS') {
    return actual === undefined;
  }
  if (actual === undefined || !runtimeTypeMatches(condition, actual)) {
    return false;
  }
  const first = condition.values[0];
  switch (condition.operator) {
    case 'EQUALS':
      return actual === first;
    case 'NOT_EQUALS':
      return actual !== first;
    case 'IN':
      return condition.values.includes(actual);
    case 'NOT_IN':
      return !condition.values.includes(actual);
    case 'STARTS_WITH':
      return (actual as string).startsWith(first as string);
    case 'ENDS_WITH':
      return (actual as string).endsWith(first as string);
    case 'CONTAINS':
      return (actual as string).includes(first as string);
    case 'EQ':
      return actual === first;
    case 'NE':
      return actual !== first;
    case 'GT':
      return (actual as number) > (first as number);
    case 'GTE':
      return (actual as number) >= (first as number);
    case 'LT':
      return (actual as number) < (first as number);
    case 'LTE':
      return (actual as number) <= (first as number);
    case 'BETWEEN_INCLUSIVE':
      return (
        (actual as number) >= (first as number) &&
        (actual as number) <= (condition.values[1] as number)
      );
    case 'IS_TRUE':
      return actual === true;
    case 'IS_FALSE':
      return actual === false;
    case 'SEMVER_EQ':
      return semanticComparison(actual, first as SemanticVersion, (comparison) => comparison === 0);
    case 'SEMVER_GT':
      return semanticComparison(actual, first as SemanticVersion, (comparison) => comparison > 0);
    case 'SEMVER_GTE':
      return semanticComparison(actual, first as SemanticVersion, (comparison) => comparison >= 0);
    case 'SEMVER_LT':
      return semanticComparison(actual, first as SemanticVersion, (comparison) => comparison < 0);
    case 'SEMVER_LTE':
      return semanticComparison(actual, first as SemanticVersion, (comparison) => comparison <= 0);
  }
}

function runtimeTypeMatches(condition: CompiledCondition, value: ContextScalar): boolean {
  if (condition.attributeType === 'string' || condition.attributeType === 'semver') {
    return typeof value === 'string';
  }
  return typeof value === condition.attributeType;
}

function semanticComparison(
  actual: ContextScalar,
  expected: SemanticVersion,
  predicate: (comparison: number) => boolean,
): boolean {
  if (typeof actual !== 'string') {
    return false;
  }
  try {
    return predicate(compareSemanticVersions(parseSemanticVersion(actual), expected));
  } catch {
    return false;
  }
}
