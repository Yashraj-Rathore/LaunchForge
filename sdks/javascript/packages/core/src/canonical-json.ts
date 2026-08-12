import type { JsonValue } from './types.js';
import { requireWellFormedUnicode } from './utf8.js';

export function canonicalize(value: JsonValue): string {
  return encode(value, 0);
}

function encode(value: JsonValue, depth: number): string {
  if (depth > 64) {
    throw new Error('JSON nesting is too deep');
  }
  if (value === null || typeof value === 'boolean') {
    return String(value);
  }
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) {
      throw new Error('JSON number is not finite');
    }
    return JSON.stringify(Object.is(value, -0) ? 0 : value);
  }
  if (typeof value === 'string') {
    requireWellFormedUnicode(value, 'JSON string');
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map((item) => encode(item, depth + 1)).join(',')}]`;
  }
  const objectValue = value as Readonly<Record<string, JsonValue>>;
  return `{${Object.keys(objectValue)
    .sort()
    .map((key) => {
      requireWellFormedUnicode(key, 'JSON property name');
      return `${JSON.stringify(key)}:${encode(objectValue[key] as JsonValue, depth + 1)}`;
    })
    .join(',')}}`;
}

export function deepFreezeJson<T extends JsonValue>(value: T): T {
  if (value !== null && typeof value === 'object') {
    if (Array.isArray(value)) {
      value.forEach((item) => deepFreezeJson(item));
    } else {
      Object.values(value).forEach((item) => deepFreezeJson(item));
    }
    Object.freeze(value);
  }
  return value;
}
