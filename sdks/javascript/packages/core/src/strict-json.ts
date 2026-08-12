import type { JsonValue } from './types.js';
import { requireWellFormedUnicode } from './utf8.js';

const NUMBER = /-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/y;
const MAX_SAFE_INTEGER = 9_007_199_254_740_991n;

export function parseStrictJson(json: string): JsonValue {
  const parser = new StrictJsonParser(json);
  const value = parser.parseValue(0);
  parser.skipWhitespace();
  if (!parser.atEnd()) {
    throw new Error('JSON has trailing tokens');
  }
  return value;
}

class StrictJsonParser {
  private index = 0;

  constructor(private readonly input: string) {}

  atEnd(): boolean {
    return this.index === this.input.length;
  }

  skipWhitespace(): void {
    while (
      /\s/u.test(this.input[this.index] ?? '') &&
      /[\x20\t\r\n]/u.test(this.input[this.index] ?? '')
    ) {
      this.index += 1;
    }
  }

  parseValue(depth: number): JsonValue {
    if (depth > 64) {
      throw new Error('JSON nesting is too deep');
    }
    this.skipWhitespace();
    const token = this.input[this.index];
    if (token === '{') {
      return this.parseObject(depth);
    }
    if (token === '[') {
      return this.parseArray(depth);
    }
    if (token === '"') {
      return this.parseString();
    }
    if (token === 't' && this.consumeLiteral('true')) {
      return true;
    }
    if (token === 'f' && this.consumeLiteral('false')) {
      return false;
    }
    if (token === 'n' && this.consumeLiteral('null')) {
      return null;
    }
    return this.parseNumber();
  }

  private parseObject(depth: number): JsonValue {
    this.index += 1;
    this.skipWhitespace();
    const result: Record<string, JsonValue> = {};
    const names = new Set<string>();
    if (this.input[this.index] === '}') {
      this.index += 1;
      return result;
    }
    while (true) {
      if (this.input[this.index] !== '"') {
        throw new Error('JSON object property must be a string');
      }
      const name = this.parseString();
      if (names.has(name)) {
        throw new Error('JSON object has a duplicate property');
      }
      names.add(name);
      this.skipWhitespace();
      this.expect(':');
      result[name] = this.parseValue(depth + 1);
      this.skipWhitespace();
      if (this.input[this.index] === '}') {
        this.index += 1;
        return result;
      }
      this.expect(',');
      this.skipWhitespace();
    }
  }

  private parseArray(depth: number): JsonValue {
    this.index += 1;
    this.skipWhitespace();
    const result: JsonValue[] = [];
    if (this.input[this.index] === ']') {
      this.index += 1;
      return result;
    }
    while (true) {
      result.push(this.parseValue(depth + 1));
      this.skipWhitespace();
      if (this.input[this.index] === ']') {
        this.index += 1;
        return result;
      }
      this.expect(',');
      this.skipWhitespace();
    }
  }

  private parseString(): string {
    const start = this.index;
    this.index += 1;
    let escaped = false;
    while (this.index < this.input.length) {
      const character = this.input[this.index];
      if (character === '"' && !escaped) {
        this.index += 1;
        const decoded: unknown = JSON.parse(this.input.slice(start, this.index));
        if (typeof decoded !== 'string') {
          throw new Error('JSON string is invalid');
        }
        return requireWellFormedUnicode(decoded, 'JSON string');
      }
      if (!escaped && character !== undefined && character.charCodeAt(0) < 0x20) {
        throw new Error('JSON string contains an unescaped control character');
      }
      if (character === '\\' && !escaped) {
        escaped = true;
      } else {
        escaped = false;
      }
      this.index += 1;
    }
    throw new Error('JSON string is unterminated');
  }

  private parseNumber(): number {
    NUMBER.lastIndex = this.index;
    const match = NUMBER.exec(this.input);
    if (match === null || match.index !== this.index) {
      throw new Error('JSON value is invalid');
    }
    const token = match[0];
    this.index = NUMBER.lastIndex;
    const value = Number(token);
    if (!Number.isFinite(value)) {
      throw new Error('JSON number is not finite binary64');
    }
    const integer = exactInteger(token);
    if (integer !== null && (integer > MAX_SAFE_INTEGER || integer < -MAX_SAFE_INTEGER)) {
      throw new Error('JSON integer exceeds the cross-SDK safe range');
    }
    return Object.is(value, -0) ? 0 : value;
  }

  private consumeLiteral(literal: string): boolean {
    if (!this.input.startsWith(literal, this.index)) {
      return false;
    }
    this.index += literal.length;
    return true;
  }

  private expect(character: string): void {
    if (this.input[this.index] !== character) {
      throw new Error(`Expected JSON token ${character}`);
    }
    this.index += 1;
  }
}

function exactInteger(token: string): bigint | null {
  const negative = token.startsWith('-');
  const unsigned = negative ? token.slice(1) : token;
  const [mantissa = '', exponentText] = unsigned.toLowerCase().split('e');
  const exponent = exponentText === undefined ? 0 : Number(exponentText);
  const [whole = '', fraction = ''] = mantissa.split('.');
  const digits = `${whole}${fraction}`;
  const decimalPlaces = fraction.length - exponent;
  if (decimalPlaces > 0) {
    const fractionalDigits = digits.slice(Math.max(0, digits.length - decimalPlaces));
    if (!/^0*$/u.test(fractionalDigits)) {
      return null;
    }
  }
  const zeros = Math.max(0, -decimalPlaces);
  const integerDigits =
    decimalPlaces > 0 ? digits.slice(0, -decimalPlaces) || '0' : digits + '0'.repeat(zeros);
  const magnitude = BigInt(integerDigits);
  return negative ? -magnitude : magnitude;
}
