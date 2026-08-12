import { sha256 } from './sha256.js';
import { requireWellFormedUnicode, utf8Bytes } from './utf8.js';

export const BUCKET_COUNT = 100_000;

export function rolloutBucket(flagKey: string, salt: string, subject: string): number {
  requireMaterial(flagKey, 'flagKey');
  requireMaterial(salt, 'salt');
  requireWellFormedUnicode(subject, 'subject');
  if (subject.length === 0) {
    throw new Error('subject is invalid rollout material');
  }
  const hash = sha256(utf8Bytes(`${flagKey}\n${salt}\n${subject}`));
  let prefix = 0n;
  for (let index = 0; index < 8; index += 1) {
    prefix = (prefix << 8n) | BigInt(hash[index] ?? 0);
  }
  return Number(prefix % BigInt(BUCKET_COUNT));
}

function requireMaterial(value: string, label: string): void {
  requireWellFormedUnicode(value, label);
  if (value.length === 0 || value.includes('\n') || value.includes('\r')) {
    throw new Error(`${label} is invalid rollout material`);
  }
}
