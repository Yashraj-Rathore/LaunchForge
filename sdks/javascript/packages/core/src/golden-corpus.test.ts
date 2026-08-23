import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { canonicalize } from './canonical-json.js';
import { matchesCondition } from './condition.js';
import { createEvaluationContext } from './context.js';
import { evaluateBoolean, evaluateJson, evaluateNumber, evaluateString } from './evaluator.js';
import { EVALUATION_REASONS } from './index.js';
import { rolloutBucket } from './rollout.js';
import { parseSemanticVersion } from './semver.js';
import { sha256Hex } from './sha256.js';
import { parseSnapshot, snapshotChecksum } from './snapshot.js';
import { parseStrictJson } from './strict-json.js';
import type {
  AttributeType,
  CompiledCondition,
  ContextScalar,
  EvaluationDetail,
  JsonValue,
  Operator,
} from './types.js';
import { utf8Bytes } from './utf8.js';

interface GoldenCorpus {
  readonly corpusChecksum: string;
  readonly reasonCodes: readonly string[];
  readonly rolloutVectors: readonly RolloutVector[];
  readonly operatorCases: readonly OperatorCase[];
  readonly evaluationSnapshot: JsonValue;
  readonly evaluationCases: readonly EvaluationCase[];
  readonly malformedSnapshots: readonly { readonly name: string; readonly snapshot: string }[];
}

interface RolloutVector {
  readonly name: string;
  readonly flagKey: string;
  readonly salt: string;
  readonly subject?: string;
  readonly bucket?: number;
  readonly sampleSize?: number;
  readonly selectedCount?: number;
  readonly subjectPrefix?: string;
  readonly thresholdExclusive?: number;
}

interface OperatorCase {
  readonly name: string;
  readonly attributeType: AttributeType;
  readonly operator: Operator;
  readonly configuredValues: readonly ContextScalar[];
  readonly contextPresent: boolean;
  readonly contextValue?: ContextScalar;
  readonly expectedMatch: boolean;
}

interface EvaluationCase {
  readonly name: string;
  readonly flagKey: string;
  readonly contextKey: string;
  readonly attributes: Readonly<Record<string, ContextScalar>>;
  readonly requestedType: 'boolean' | 'string' | 'number' | 'json';
  readonly defaultValue: JsonValue;
  readonly expected: Readonly<Record<string, JsonValue>>;
}

interface JsonVariationSizeCorpus {
  readonly schemaVersion: 1;
  readonly maximumCanonicalUtf8Bytes: number;
  readonly codePoint: string;
  readonly accepted: JsonVariationSizeBoundary;
  readonly rejected: JsonVariationSizeBoundary;
}

interface JsonVariationSizeBoundary {
  readonly repeatCount: number;
  readonly canonicalUtf8Bytes: number;
}

const corpusPath = fileURLToPath(
  new URL('../../../../../contracts/golden-vectors/evaluator-v1.json', import.meta.url),
);
const corpusJson = readFileSync(corpusPath, 'utf8');
const corpus = JSON.parse(corpusJson) as GoldenCorpus;
const jsonVariationSizePath = fileURLToPath(
  new URL('../../../../../contracts/golden-vectors/json-variation-size-v1.json', import.meta.url),
);
const jsonVariationSize = JSON.parse(
  readFileSync(jsonVariationSizePath, 'utf8'),
) as JsonVariationSizeCorpus;

describe('algorithm-version-1 shared golden corpus', () => {
  it('verifies the frozen corpus checksum and reason identifiers', () => {
    const parsed = parseStrictJson(corpusJson) as Readonly<Record<string, JsonValue>>;
    const projection: Record<string, JsonValue> = {};
    for (const [key, value] of Object.entries(parsed)) {
      if (key !== 'corpusChecksum') {
        projection[key] = value;
      }
    }
    expect(sha256Hex(canonicalize(projection))).toBe(corpus.corpusChecksum);
    expect(EVALUATION_REASONS).toEqual(corpus.reasonCodes);
  });

  it('matches every generated rollout vector and deterministic sample', () => {
    for (const vector of corpus.rolloutVectors) {
      if (vector.subject !== undefined) {
        expect(rolloutBucket(vector.flagKey, vector.salt, vector.subject), vector.name).toBe(
          vector.bucket,
        );
      } else {
        let selected = 0;
        for (let index = 0; index < (vector.sampleSize as number); index += 1) {
          const bucket = rolloutBucket(
            vector.flagKey,
            vector.salt,
            `${vector.subjectPrefix as string}${index}`,
          );
          if (bucket < (vector.thresholdExclusive as number)) {
            selected += 1;
          }
        }
        expect(selected, vector.name).toBe(vector.selectedCount);
      }
    }
  });

  it('matches every targeting operator case', () => {
    for (const testCase of corpus.operatorCases) {
      const values =
        testCase.attributeType === 'semver'
          ? testCase.configuredValues.map((value) => parseSemanticVersion(value as string))
          : testCase.configuredValues;
      const condition: CompiledCondition = {
        attribute: 'attribute',
        attributeType: testCase.attributeType,
        operator: testCase.operator,
        values,
      };
      const attributes =
        testCase.contextPresent && testCase.contextValue !== undefined
          ? { attribute: testCase.contextValue }
          : {};
      expect(
        matchesCondition(condition, createEvaluationContext('subject', attributes)),
        testCase.name,
      ).toBe(testCase.expectedMatch);
    }
  });

  it('returns the exact typed evaluation outcomes', () => {
    const snapshot = parseSnapshot(JSON.stringify(corpus.evaluationSnapshot));
    for (const testCase of corpus.evaluationCases) {
      const context = createEvaluationContext(testCase.contextKey, testCase.attributes);
      let detail: EvaluationDetail<unknown>;
      switch (testCase.requestedType) {
        case 'boolean':
          detail = evaluateBoolean(
            snapshot,
            testCase.flagKey,
            context,
            testCase.defaultValue as boolean,
          );
          break;
        case 'string':
          detail = evaluateString(
            snapshot,
            testCase.flagKey,
            context,
            testCase.defaultValue as string,
          );
          break;
        case 'number':
          detail = evaluateNumber(
            snapshot,
            testCase.flagKey,
            context,
            testCase.defaultValue as number,
          );
          break;
        case 'json':
          detail = evaluateJson(snapshot, testCase.flagKey, context, testCase.defaultValue);
          break;
      }
      expect(toCorpusDetail(detail), testCase.name).toEqual(testCase.expected);
    }
  });

  it('rejects every malformed snapshot', () => {
    for (const malformed of corpus.malformedSnapshots) {
      expect(() => parseSnapshot(malformed.snapshot), malformed.name).toThrow();
    }
  });

  it('enforces shared canonical UTF-8 JSON variation size boundaries', () => {
    const accepted = jsonVariationSize.codePoint.repeat(jsonVariationSize.accepted.repeatCount);
    const rejected = jsonVariationSize.codePoint.repeat(jsonVariationSize.rejected.repeatCount);

    expect(utf8Bytes(canonicalize(accepted)).length).toBe(
      jsonVariationSize.accepted.canonicalUtf8Bytes,
    );
    expect(utf8Bytes(canonicalize(rejected)).length).toBe(
      jsonVariationSize.rejected.canonicalUtf8Bytes,
    );
    expect(jsonVariationSize.maximumCanonicalUtf8Bytes).toBe(64 * 1024);
    expect(() => parseSnapshot(jsonVariationSnapshot(accepted))).not.toThrow();
    expect(() => parseSnapshot(jsonVariationSnapshot(rejected))).toThrow(
      'Canonical JSON value exceeds 64 KiB',
    );
  });
});

function jsonVariationSnapshot(boundaryValue: string): string {
  const root: Record<string, JsonValue> = {
    schemaVersion: 1,
    algorithmVersion: 1,
    projectKey: 'demo-project',
    environmentKey: 'test',
    revision: 1,
    generatedAt: '2026-08-11T12:00:00Z',
    flags: {
      'json-boundary': {
        type: 'json',
        enabled: true,
        clientVisible: true,
        variations: [
          { id: 'boundary', value: boundaryValue },
          { id: 'fallback', value: {} },
        ],
        offVariation: 'fallback',
        defaultVariation: 'boundary',
        rules: [],
      },
    },
  };
  root.checksum = snapshotChecksum(root);
  return JSON.stringify(root);
}

function toCorpusDetail(detail: EvaluationDetail<unknown>): Readonly<Record<string, JsonValue>> {
  return {
    value: detail.value as JsonValue,
    reason: detail.reason,
    ...(detail.variationId === undefined ? {} : { variation: detail.variationId }),
    ...(detail.matchedRuleId === undefined ? {} : { rule: detail.matchedRuleId }),
    ...(detail.snapshotRevision === undefined ? {} : { revision: detail.snapshotRevision }),
  };
}
