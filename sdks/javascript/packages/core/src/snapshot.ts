import { canonicalize, deepFreezeJson } from './canonical-json.js';
import { parseSemanticVersion } from './semver.js';
import { sha256Hex } from './sha256.js';
import { parseStrictJson } from './strict-json.js';
import type {
  AttributeType,
  CompiledAllocation,
  CompiledCondition,
  CompiledFlag,
  CompiledRollout,
  CompiledRule,
  CompiledSnapshot,
  ContextScalar,
  FlagType,
  JsonValue,
  Operator,
} from './types.js';
import { codePointLength, requireWellFormedUnicode, utf8Bytes } from './utf8.js';

export const SNAPSHOT_SCHEMA_VERSION = 1;
export const EVALUATION_ALGORITHM_VERSION = 1;
export const MAX_SNAPSHOT_BYTES = 5 * 1024 * 1024;

const KEY = /^[a-z][a-z0-9._-]{0,63}$/u;
const ATTRIBUTE = /^[A-Za-z][A-Za-z0-9_.-]{0,63}$/u;
const CHECKSUM = /^[0-9a-f]{64}$/u;
const SALT = /^[A-Za-z0-9_-]{16,128}$/u;
const INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/u;
const FLAG_TYPES = new Set<FlagType>(['boolean', 'string', 'number', 'json']);
const ATTRIBUTE_TYPES = new Set<AttributeType>(['string', 'number', 'boolean', 'semver']);
const OPERATORS = new Set<Operator>([
  'EQUALS',
  'NOT_EQUALS',
  'IN',
  'NOT_IN',
  'STARTS_WITH',
  'ENDS_WITH',
  'CONTAINS',
  'EQ',
  'NE',
  'GT',
  'GTE',
  'LT',
  'LTE',
  'BETWEEN_INCLUSIVE',
  'IS_TRUE',
  'IS_FALSE',
  'SEMVER_EQ',
  'SEMVER_GT',
  'SEMVER_GTE',
  'SEMVER_LT',
  'SEMVER_LTE',
  'EXISTS',
  'NOT_EXISTS',
]);

export class SnapshotValidationError extends Error {
  constructor(message: string, options?: ErrorOptions) {
    super(message, options);
    this.name = 'SnapshotValidationError';
  }
}

export function parseSnapshot(json: string): CompiledSnapshot {
  if (typeof json !== 'string' || utf8Bytes(json).length > MAX_SNAPSHOT_BYTES) {
    throw new SnapshotValidationError('Snapshot is absent or exceeds 5 MiB');
  }
  try {
    const parsed = parseStrictJson(json);
    const root = object(parsed, 'Snapshot root');
    const schemaVersion = integer(root, 'schemaVersion');
    const algorithmVersion = integer(root, 'algorithmVersion');
    if (
      schemaVersion !== SNAPSHOT_SCHEMA_VERSION ||
      algorithmVersion !== EVALUATION_ALGORITHM_VERSION
    ) {
      throw invalid('Snapshot schema or algorithm version is unsupported');
    }
    const projectKey = key(root, 'projectKey');
    const environmentKey = key(root, 'environmentKey');
    const revision = positiveSafeInteger(root, 'revision');
    const generatedAt = text(root, 'generatedAt');
    if (!INSTANT.test(generatedAt) || !Number.isFinite(Date.parse(generatedAt))) {
      throw invalid('Snapshot timestamp is invalid: generatedAt');
    }
    const checksum = text(root, 'checksum');
    if (!CHECKSUM.test(checksum) || checksum !== snapshotChecksum(root)) {
      throw invalid('Snapshot checksum is invalid');
    }
    const flagsNode = object(required(root, 'flags'), 'flags');
    const entries = Object.entries(flagsNode);
    if (entries.length > 2_000) {
      throw invalid('Snapshot has too many flags');
    }
    const flags: Record<string, CompiledFlag> = {};
    for (const [flagKey, value] of entries) {
      requireKey(flagKey, 'Flag key');
      flags[flagKey] = parseFlag(object(value, 'Flag'));
    }
    return Object.freeze({
      schemaVersion: 1,
      algorithmVersion: 1,
      projectKey,
      environmentKey,
      revision,
      generatedAt,
      checksum,
      flags: Object.freeze(flags),
    });
  } catch (error) {
    if (error instanceof SnapshotValidationError) {
      throw error;
    }
    throw invalid('Snapshot is not valid version-1 configuration', error);
  }
}

export function snapshotChecksum(root: Readonly<Record<string, JsonValue>>): string {
  const projection: Record<string, JsonValue> = {};
  for (const [name, value] of Object.entries(root)) {
    if (name !== 'checksum') {
      projection[name] = value;
    }
  }
  return sha256Hex(canonicalize(projection));
}

function parseFlag(node: Readonly<Record<string, JsonValue>>): CompiledFlag {
  const typeValue = text(node, 'type');
  if (!FLAG_TYPES.has(typeValue as FlagType)) {
    throw invalid('Flag type is unsupported');
  }
  const type = typeValue as FlagType;
  const enabled = boolean(node, 'enabled');
  const clientVisible = boolean(node, 'clientVisible');
  const variations = parseVariations(array(node, 'variations'), type);
  const offVariation = variationReference(node, 'offVariation', variations);
  const defaultVariation = variationReference(node, 'defaultVariation', variations);
  const rules = parseRules(array(node, 'rules'), variations);
  const rolloutValue = node.rollout;
  const rollout = rolloutValue === undefined ? undefined : parseRollout(rolloutValue, variations);
  const compiled: CompiledFlag = {
    type,
    enabled,
    clientVisible,
    variations,
    offVariation,
    defaultVariation,
    rules,
    ...(rollout === undefined ? {} : { rollout }),
  };
  return Object.freeze(compiled);
}

function parseVariations(
  values: readonly JsonValue[],
  type: FlagType,
): Readonly<Record<string, boolean | string | number | JsonValue>> {
  if (values.length === 0 || values.length > 50) {
    throw invalid('Variation count is invalid');
  }
  const variations: Record<string, boolean | string | number | JsonValue> = {};
  for (const value of values) {
    const variation = object(value, 'Variation');
    const id = key(variation, 'id');
    if (Object.hasOwn(variations, id)) {
      throw invalid('Variation IDs must be unique');
    }
    const raw = required(variation, 'value');
    variations[id] = parseVariationValue(raw, type);
  }
  return Object.freeze(variations);
}

function parseVariationValue(
  value: JsonValue,
  type: FlagType,
): boolean | string | number | JsonValue {
  if (type === 'boolean' && typeof value !== 'boolean') {
    throw invalid('Boolean variation value has the wrong type');
  }
  if (type === 'string' && typeof value !== 'string') {
    throw invalid('String variation value has the wrong type');
  }
  if (type === 'number' && typeof value !== 'number') {
    throw invalid('Number variation value has the wrong type');
  }
  if (type === 'json') {
    const canonical = canonicalize(value);
    if (utf8Bytes(canonical).length > 64 * 1024) {
      throw invalid('Canonical JSON value exceeds 64 KiB');
    }
    return deepFreezeJson(value);
  }
  return value;
}

function parseRules(
  values: readonly JsonValue[],
  variations: Readonly<Record<string, unknown>>,
): readonly CompiledRule[] {
  if (values.length > 100) {
    throw invalid('Flag has too many rules');
  }
  const rules: CompiledRule[] = [];
  const ids = new Set<string>();
  for (const value of values) {
    const rule = object(value, 'Rule');
    const id = text(rule, 'id');
    if (id.trim().length === 0 || id.length > 128 || ids.has(id)) {
      throw invalid('Rule ID is invalid or duplicated');
    }
    ids.add(id);
    const conditionsNode = array(rule, 'conditions');
    if (conditionsNode.length === 0 || conditionsNode.length > 10) {
      throw invalid('Rule condition count is invalid');
    }
    rules.push(
      Object.freeze({
        id,
        conditions: Object.freeze(conditionsNode.map((condition) => parseCondition(condition))),
        variation: variationReference(rule, 'variation', variations),
      }),
    );
  }
  return Object.freeze(rules);
}

function parseCondition(value: JsonValue): CompiledCondition {
  const condition = object(value, 'Condition');
  const attribute = text(condition, 'attribute');
  if (!ATTRIBUTE.test(attribute)) {
    throw invalid('Targeting attribute is invalid');
  }
  const attributeTypeValue = text(condition, 'attributeType');
  const operatorValue = text(condition, 'operator');
  if (
    !ATTRIBUTE_TYPES.has(attributeTypeValue as AttributeType) ||
    !OPERATORS.has(operatorValue as Operator)
  ) {
    throw invalid('Condition type or operator is unsupported');
  }
  const attributeType = attributeTypeValue as AttributeType;
  const operator = operatorValue as Operator;
  requireOperatorType(operator, attributeType);
  const valuesNode = array(condition, 'values');
  requireArity(operator, valuesNode.length);
  return Object.freeze({
    attribute,
    attributeType,
    operator,
    values: Object.freeze(valuesNode.map((item) => parseConditionValue(attributeType, item))),
  });
}

function parseConditionValue(
  attributeType: AttributeType,
  value: JsonValue,
): ContextScalar | ReturnType<typeof parseSemanticVersion> {
  if (attributeType === 'string') {
    if (typeof value !== 'string' || codePointLength(value) > 1024) {
      throw invalid('String condition value has the wrong type or is too long');
    }
    return value;
  }
  if (attributeType === 'number') {
    if (typeof value !== 'number') {
      throw invalid('Number condition value has the wrong type');
    }
    return value;
  }
  if (attributeType === 'boolean') {
    throw invalid('Boolean conditions do not accept configured values');
  }
  if (typeof value !== 'string') {
    throw invalid('Semantic version condition value has the wrong type');
  }
  try {
    return parseSemanticVersion(value);
  } catch (error) {
    throw invalid('Semantic version condition value is invalid', error);
  }
}

function parseRollout(
  value: JsonValue,
  variations: Readonly<Record<string, unknown>>,
): CompiledRollout {
  const rollout = object(value, 'Rollout');
  const attribute = text(rollout, 'attribute');
  if (!ATTRIBUTE.test(attribute)) {
    throw invalid('Rollout attribute is invalid');
  }
  const salt = text(rollout, 'salt');
  if (!SALT.test(salt)) {
    throw invalid('Rollout salt is invalid');
  }
  const weights = array(rollout, 'weights');
  if (weights.length === 0 || weights.length > 50) {
    throw invalid('Rollout allocation count is invalid');
  }
  const seen = new Set<string>();
  const allocations: CompiledAllocation[] = [];
  let total = 0;
  for (const item of weights) {
    const allocation = object(item, 'Rollout allocation');
    const variation = variationReference(allocation, 'variation', variations);
    if (seen.has(variation)) {
      throw invalid('Rollout variation IDs must be unique');
    }
    seen.add(variation);
    const weight = integer(allocation, 'weight');
    if (weight <= 0 || weight > 100_000) {
      throw invalid('Rollout weight is invalid');
    }
    total += weight;
    allocations.push(Object.freeze({ variation, upperExclusive: total }));
  }
  if (total !== 100_000) {
    throw invalid('Rollout weights must total 100000');
  }
  return Object.freeze({ attribute, salt, allocations: Object.freeze(allocations) });
}

function requireOperatorType(operator: Operator, type: AttributeType): void {
  const stringOperators = new Set<Operator>([
    'EQUALS',
    'NOT_EQUALS',
    'IN',
    'NOT_IN',
    'STARTS_WITH',
    'ENDS_WITH',
    'CONTAINS',
  ]);
  const numberOperators = new Set<Operator>([
    'EQ',
    'NE',
    'GT',
    'GTE',
    'LT',
    'LTE',
    'BETWEEN_INCLUSIVE',
  ]);
  const booleanOperators = new Set<Operator>(['IS_TRUE', 'IS_FALSE']);
  const semverOperators = new Set<Operator>([
    'SEMVER_EQ',
    'SEMVER_GT',
    'SEMVER_GTE',
    'SEMVER_LT',
    'SEMVER_LTE',
  ]);
  if (operator === 'EXISTS' || operator === 'NOT_EXISTS') {
    return;
  }
  const valid =
    (type === 'string' && stringOperators.has(operator)) ||
    (type === 'number' && numberOperators.has(operator)) ||
    (type === 'boolean' && booleanOperators.has(operator)) ||
    (type === 'semver' && semverOperators.has(operator));
  if (!valid) {
    throw invalid('Operator is invalid for attribute type');
  }
}

function requireArity(operator: Operator, count: number): void {
  const valid =
    (['EXISTS', 'NOT_EXISTS', 'IS_TRUE', 'IS_FALSE'].includes(operator) && count === 0) ||
    (operator === 'BETWEEN_INCLUSIVE' && count === 2) ||
    (['IN', 'NOT_IN'].includes(operator) && count >= 1 && count <= 50) ||
    (!['EXISTS', 'NOT_EXISTS', 'IS_TRUE', 'IS_FALSE', 'BETWEEN_INCLUSIVE', 'IN', 'NOT_IN'].includes(
      operator,
    ) &&
      count === 1);
  if (!valid) {
    throw invalid('Condition value count is invalid');
  }
}

function required(node: Readonly<Record<string, JsonValue>>, field: string): JsonValue {
  const value = node[field];
  if (value === undefined || value === null) {
    throw invalid(`Required snapshot field is absent: ${field}`);
  }
  return value;
}

function object(value: JsonValue, label: string): Readonly<Record<string, JsonValue>> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw invalid(`${label} must be an object`);
  }
  return value as Readonly<Record<string, JsonValue>>;
}

function array(node: Readonly<Record<string, JsonValue>>, field: string): readonly JsonValue[] {
  const value = required(node, field);
  if (!Array.isArray(value)) {
    throw invalid(`Snapshot field must be an array: ${field}`);
  }
  return value;
}

function text(node: Readonly<Record<string, JsonValue>>, field: string): string {
  const value = required(node, field);
  if (typeof value !== 'string') {
    throw invalid(`Snapshot field must be a string: ${field}`);
  }
  return requireWellFormedUnicode(value, field);
}

function key(node: Readonly<Record<string, JsonValue>>, field: string): string {
  return requireKey(text(node, field), field);
}

function requireKey(value: string, label: string): string {
  if (!KEY.test(value)) {
    throw invalid(`${label} is not a canonical key`);
  }
  return value;
}

function variationReference(
  node: Readonly<Record<string, JsonValue>>,
  field: string,
  variations: Readonly<Record<string, unknown>>,
): string {
  const reference = key(node, field);
  if (!Object.hasOwn(variations, reference)) {
    throw invalid(`Variation reference is unknown: ${field}`);
  }
  return reference;
}

function boolean(node: Readonly<Record<string, JsonValue>>, field: string): boolean {
  const value = required(node, field);
  if (typeof value !== 'boolean') {
    throw invalid(`Snapshot field must be a boolean: ${field}`);
  }
  return value;
}

function integer(node: Readonly<Record<string, JsonValue>>, field: string): number {
  const value = required(node, field);
  if (
    typeof value !== 'number' ||
    !Number.isInteger(value) ||
    value < -2_147_483_648 ||
    value > 2_147_483_647
  ) {
    throw invalid(`Snapshot field must be a 32-bit integer: ${field}`);
  }
  return value;
}

function positiveSafeInteger(node: Readonly<Record<string, JsonValue>>, field: string): number {
  const value = required(node, field);
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value <= 0) {
    throw invalid(`Snapshot field must be a positive safe integer: ${field}`);
  }
  return value;
}

function invalid(message: string, cause?: unknown): SnapshotValidationError {
  return new SnapshotValidationError(message, cause === undefined ? undefined : { cause });
}
