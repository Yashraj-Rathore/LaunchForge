import { describe, expect, it } from 'vitest';
import { createEvaluationContext } from './context.js';
import { evaluateBoolean } from './evaluator.js';
import { parseStrictJson } from './strict-json.js';

describe('core safety boundaries', () => {
  it('treats null context attributes as missing and validates scalar limits', () => {
    expect(createEvaluationContext('user-1', { plan: null }).attributes).toEqual({});
    expect(() => createEvaluationContext('', {})).toThrow('Context key is empty');
    expect(() => createEvaluationContext('user-1', { key: 'reserved' })).toThrow(
      'invalid or reserved',
    );
    expect(() => createEvaluationContext('user-1', { score: Number.NaN })).toThrow('finite');
  });

  it('returns a bounded unavailable detail before bootstrap', () => {
    const detail = evaluateBoolean(null, 'new-checkout', createEvaluationContext('user-1'), false);
    expect(detail).toEqual({
      value: false,
      reason: 'SNAPSHOT_UNAVAILABLE',
      errorKind: 'SNAPSHOT_UNAVAILABLE',
    });
  });

  it('rejects duplicate properties and unpaired Unicode', () => {
    expect(() => parseStrictJson('{"value":1,"value":2}')).toThrow('duplicate');
    expect(() => parseStrictJson('"\\ud800"')).toThrow('unpaired surrogate');
  });
});
