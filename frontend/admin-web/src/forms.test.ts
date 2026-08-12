import { describe, expect, test } from 'vitest';
import { createFlagPayload, moveItem, parseVariationValue, serializeDraft } from './forms';
import type { Rule } from './types';

describe('typed flag form contracts', () => {
  test('serializes typed values without implicit coercion', () => {
    const payload = createFlagPayload({
      key: 'checkout-copy',
      name: 'Checkout copy',
      type: 'JSON',
      clientVisible: true,
      variations: [
        { key: 'control', name: 'Control', rawValue: '{"text":"Buy"}' },
        { key: 'treatment', name: 'Treatment', rawValue: '{"text":"Checkout"}' },
      ],
    });

    expect(payload.variations[1]?.value).toEqual({ text: 'Checkout' });
    expect(() => parseVariationValue('BOOLEAN', 'yes')).toThrow(/true or false/u);
    expect(() => parseVariationValue('NUMBER', 'Infinity')).toThrow(/finite/u);
  });

  test('preserves rule order and accepts evaluator attribute casing', () => {
    const first: Rule = {
      id: 'rule-one',
      name: 'Canadian visitors',
      conditions: [
        {
          attribute: 'geo.Country',
          attributeType: 'STRING',
          operator: 'EQUALS',
          values: ['CA'],
        },
      ],
      variationId: 'on',
    };
    const second: Rule = {
      id: 'rule-two',
      name: 'US visitors',
      conditions: [
        {
          attribute: 'geo.Country',
          attributeType: 'STRING',
          operator: 'EQUALS',
          values: ['US'],
        },
      ],
      variationId: 'on',
    };
    const reordered = moveItem([first, second], 1, -1);

    const payload = serializeDraft({
      enabled: true,
      fallthroughVariationId: 'off',
      offVariationId: 'off',
      rules: reordered,
      rolloutEnabled: true,
      subjectAttribute: 'user.Key',
      allocations: [
        { variationId: 'off', weight: 90_000 },
        { variationId: 'on', weight: 10_000 },
      ],
      changeSummary: 'A'.repeat(300),
    });

    expect(payload.rules.map((rule) => rule.id)).toEqual(['rule-two', 'rule-one']);
    expect(
      payload.rollout?.allocations.reduce((sum, allocation) => sum + allocation.weight, 0),
    ).toBe(100_000);
  });

  test('rejects operators that do not belong to the selected attribute type', () => {
    expect(() =>
      serializeDraft({
        enabled: true,
        fallthroughVariationId: 'off',
        offVariationId: 'off',
        rules: [
          {
            id: 'bad-rule',
            name: 'Bad operator',
            conditions: [
              {
                attribute: 'country',
                attributeType: 'BOOLEAN',
                operator: 'CONTAINS',
                values: ['true'],
              },
            ],
            variationId: 'on',
          },
        ],
        rolloutEnabled: false,
        subjectAttribute: 'key',
        allocations: [],
        changeSummary: 'Validate form',
      }),
    ).toThrow(/operator is invalid/u);
  });
});
