import type { ContextScalar, EvaluationContext } from './types.js';
import { codePointLength, requireWellFormedUnicode, utf8Bytes } from './utf8.js';

const ATTRIBUTE_NAME = /^[A-Za-z][A-Za-z0-9_.-]{0,127}$/u;

export function createEvaluationContext(
  key: string,
  attributes: Readonly<Record<string, ContextScalar | null | undefined>> = {},
): EvaluationContext {
  requireString(key, 'Context key');
  if (key.length === 0) {
    throw new Error('Context key is empty');
  }
  const copied: Record<string, ContextScalar> = {};
  for (const [name, value] of Object.entries(attributes)) {
    requireWellFormedUnicode(name, 'Attribute name');
    if (name === 'key' || !ATTRIBUTE_NAME.test(name) || codePointLength(name) > 128) {
      throw new Error('Attribute name is invalid or reserved');
    }
    if (value === null || value === undefined) {
      continue;
    }
    if (typeof value === 'string') {
      requireString(value, 'String attribute');
    } else if (typeof value === 'number') {
      if (!Number.isFinite(value)) {
        throw new Error('Number attribute must be finite binary64');
      }
      copied[name] = Object.is(value, -0) ? 0 : value;
      continue;
    } else if (typeof value !== 'boolean') {
      throw new Error('Context attributes must be scalar');
    }
    copied[name] = value;
  }
  if (Object.keys(copied).length > 64) {
    throw new Error('Evaluation context has more than 64 attributes');
  }
  const context = Object.freeze({ key, attributes: Object.freeze(copied) });
  if (utf8Bytes(JSON.stringify(context)).length > 16 * 1024) {
    throw new Error('Evaluation context exceeds 16 KiB');
  }
  return context;
}

function requireString(value: string, label: string): void {
  requireWellFormedUnicode(value, label);
  if (codePointLength(value) > 1024) {
    throw new Error(`${label} is too long`);
  }
}
