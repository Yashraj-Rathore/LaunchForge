import { z } from 'zod';
import type {
  Allocation,
  AttributeType,
  Condition,
  Draft,
  FlagType,
  Operator,
  Rule,
  Variation,
} from './types';

const keySchema = z.string().regex(/^[a-z][a-z0-9._-]{0,63}$/u, 'Use a canonical lowercase key.');
const attributeSchema = z
  .string()
  .regex(/^[A-Za-z][A-Za-z0-9._-]{0,63}$/u, 'Use a valid evaluation-context attribute.');
const nameSchema = z.string().trim().min(1).max(120);
const changeSummarySchema = z.string().trim().min(1).max(500);

export const operatorsByType: Readonly<Record<AttributeType, readonly Operator[]>> = {
  STRING: [
    'EQUALS',
    'NOT_EQUALS',
    'IN',
    'NOT_IN',
    'STARTS_WITH',
    'ENDS_WITH',
    'CONTAINS',
    'EXISTS',
    'NOT_EXISTS',
  ],
  NUMBER: ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'BETWEEN_INCLUSIVE', 'EXISTS', 'NOT_EXISTS'],
  BOOLEAN: ['IS_TRUE', 'IS_FALSE', 'EXISTS', 'NOT_EXISTS'],
  SEMVER: ['SEMVER_EQ', 'SEMVER_GT', 'SEMVER_GTE', 'SEMVER_LT', 'SEMVER_LTE'],
};

export interface EditableVariation {
  readonly id?: string;
  readonly key: string;
  readonly name: string;
  readonly rawValue: string;
}

export function parseVariationValue(type: FlagType, raw: string): unknown {
  if (type === 'STRING') return raw;
  if (type === 'BOOLEAN') {
    if (raw !== 'true' && raw !== 'false') throw new Error('Boolean values must be true or false.');
    return raw === 'true';
  }
  if (type === 'NUMBER') {
    const value = Number(raw);
    if (!Number.isFinite(value)) throw new Error('Number values must be finite.');
    if (Number.isInteger(value) && !Number.isSafeInteger(value)) {
      throw new Error('Integer values must be JavaScript-safe.');
    }
    return Object.is(value, -0) ? 0 : value;
  }
  try {
    return JSON.parse(raw) as unknown;
  } catch {
    throw new Error('JSON variation values must be valid JSON.');
  }
}

export function createFlagPayload(input: {
  readonly key: string;
  readonly name: string;
  readonly type: FlagType;
  readonly clientVisible: boolean;
  readonly variations: readonly EditableVariation[];
}) {
  keySchema.parse(input.key);
  nameSchema.parse(input.name);
  if (input.variations.length < 2 || input.variations.length > 10) {
    throw new Error('A flag requires between 2 and 10 variations.');
  }
  const keys = new Set<string>();
  return {
    key: input.key,
    name: input.name.trim(),
    type: input.type,
    clientVisible: input.clientVisible,
    variations: input.variations.map((variation) => {
      keySchema.parse(variation.key);
      nameSchema.parse(variation.name);
      if (!keys.add(variation.key)) throw new Error('Variation keys must be unique.');
      return {
        key: variation.key,
        name: variation.name.trim(),
        value: parseVariationValue(input.type, variation.rawValue),
      };
    }),
  };
}

export function updateFlagPayload(
  name: string,
  status: 'ACTIVE' | 'ARCHIVED',
  type: FlagType,
  variations: readonly EditableVariation[],
) {
  nameSchema.parse(name);
  return {
    name: name.trim(),
    status,
    variations: variations.map((variation) => {
      if (variation.id === undefined) throw new Error('Existing variation identity is missing.');
      nameSchema.parse(variation.name);
      return {
        id: variation.id,
        name: variation.name.trim(),
        value: parseVariationValue(type, variation.rawValue),
      };
    }),
  };
}

export function editableVariations(values: readonly Variation[]): EditableVariation[] {
  return values.map((variation) => ({
    id: variation.id,
    key: variation.key,
    name: variation.name,
    rawValue:
      typeof variation.value === 'string' ? variation.value : JSON.stringify(variation.value),
  }));
}

export interface EditableDraft {
  readonly enabled: boolean;
  readonly fallthroughVariationId: string;
  readonly offVariationId: string;
  readonly rules: readonly Rule[];
  readonly rolloutEnabled: boolean;
  readonly subjectAttribute: string;
  readonly allocations: readonly Allocation[];
  readonly changeSummary: string;
}

export function editableDraft(draft: Draft): EditableDraft {
  return {
    enabled: draft.enabled,
    fallthroughVariationId: draft.fallthroughVariationId,
    offVariationId: draft.offVariationId,
    rules: draft.rules,
    rolloutEnabled: draft.rollout !== null,
    subjectAttribute: draft.rollout?.subjectAttribute ?? 'key',
    allocations: draft.rollout?.allocations ?? [],
    changeSummary: draft.changeSummary,
  };
}

export function serializeDraft(value: EditableDraft) {
  changeSummarySchema.parse(value.changeSummary);
  const ruleIds = new Set<string>();
  for (const rule of value.rules) {
    if (!ruleIds.add(rule.id)) throw new Error('Rule identities must be unique.');
    nameSchema.parse(rule.name);
    if (rule.conditions.length === 0 || rule.conditions.length > 10) {
      throw new Error('Every rule needs between 1 and 10 conditions.');
    }
    for (const condition of rule.conditions) validateCondition(condition);
  }
  let rollout = null;
  if (value.rolloutEnabled) {
    attributeSchema.parse(value.subjectAttribute);
    if (value.allocations.length === 0) throw new Error('Rollout allocations are required.');
    const total = value.allocations.reduce((sum, allocation) => sum + allocation.weight, 0);
    if (total !== 100_000) throw new Error('Rollout allocations must total exactly 100.000%.');
    rollout = { subjectAttribute: value.subjectAttribute, allocations: value.allocations };
  }
  return {
    enabled: value.enabled,
    fallthroughVariationId: value.fallthroughVariationId,
    offVariationId: value.offVariationId,
    rules: value.rules,
    rollout,
    changeSummary: value.changeSummary.trim(),
  };
}

function validateCondition(condition: Condition): void {
  attributeSchema.parse(condition.attribute);
  if (!operatorsByType[condition.attributeType].includes(condition.operator)) {
    throw new Error('The selected operator is invalid for this attribute type.');
  }
  const zeroValues = ['EXISTS', 'NOT_EXISTS', 'IS_TRUE', 'IS_FALSE'].includes(condition.operator);
  const expected = condition.operator === 'BETWEEN_INCLUSIVE' ? 2 : zeroValues ? 0 : 1;
  if (['IN', 'NOT_IN'].includes(condition.operator)) {
    if (condition.values.length < 1 || condition.values.length > 50) {
      throw new Error('Membership operators need one to 50 values.');
    }
  } else if (condition.values.length !== expected) {
    throw new Error('Condition values do not match the selected operator.');
  }
}

export function moveItem<T>(values: readonly T[], index: number, direction: -1 | 1): T[] {
  const destination = index + direction;
  if (destination < 0 || destination >= values.length) return [...values];
  const next = [...values];
  const current = next[index];
  const target = next[destination];
  if (current === undefined || target === undefined) return next;
  next[index] = target;
  next[destination] = current;
  return next;
}
