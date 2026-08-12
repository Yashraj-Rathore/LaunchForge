import type { SemanticIdentifier, SemanticVersion } from './types.js';

const SYNTAX =
  /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*)(?:\.(?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*))*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$/u;

export function parseSemanticVersion(value: string): SemanticVersion {
  const match = SYNTAX.exec(value);
  if (match === null) {
    throw new Error('Invalid semantic version');
  }
  return Object.freeze({
    major: BigInt(match[1] as string),
    minor: BigInt(match[2] as string),
    patch: BigInt(match[3] as string),
    prerelease: Object.freeze(
      match[4] === undefined
        ? []
        : match[4].split('.').map((identifier) => parseIdentifier(identifier)),
    ),
  });
}

export function compareSemanticVersions(left: SemanticVersion, right: SemanticVersion): number {
  for (const field of ['major', 'minor', 'patch'] as const) {
    if (left[field] !== right[field]) {
      return left[field] < right[field] ? -1 : 1;
    }
  }
  if (left.prerelease.length === 0) {
    return right.prerelease.length === 0 ? 0 : 1;
  }
  if (right.prerelease.length === 0) {
    return -1;
  }
  const sharedLength = Math.min(left.prerelease.length, right.prerelease.length);
  for (let index = 0; index < sharedLength; index += 1) {
    const comparison = compareIdentifiers(
      left.prerelease[index] as SemanticIdentifier,
      right.prerelease[index] as SemanticIdentifier,
    );
    if (comparison !== 0) {
      return comparison;
    }
  }
  return Math.sign(left.prerelease.length - right.prerelease.length);
}

function parseIdentifier(text: string): SemanticIdentifier {
  return /^\d+$/u.test(text)
    ? Object.freeze({ text, numeric: BigInt(text) })
    : Object.freeze({ text });
}

function compareIdentifiers(left: SemanticIdentifier, right: SemanticIdentifier): number {
  if (left.numeric !== undefined && right.numeric !== undefined) {
    return left.numeric === right.numeric ? 0 : left.numeric < right.numeric ? -1 : 1;
  }
  if (left.numeric !== undefined) {
    return -1;
  }
  if (right.numeric !== undefined) {
    return 1;
  }
  return left.text === right.text ? 0 : left.text < right.text ? -1 : 1;
}
