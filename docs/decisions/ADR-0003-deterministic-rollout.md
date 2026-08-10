# ADR-0003 - Versioned Deterministic Percentage Rollout Algorithm

- Status: Accepted
- Date: 2026-08-10

## Context

Percentage rollouts must assign the same subject consistently and identically across Java and JavaScript SDKs.

Language-native hashes, random numbers, floating-point percentages, or undocumented concatenation would drift.

## Decision

Algorithm version 1:

1. bucket count = `100000`;
2. material = UTF-8 bytes of `<flagKey>\n<rolloutSalt>\n<subjectAttributeValue>`;
3. hash = SHA-256(material);
4. interpret the first 8 hash bytes as an **unsigned big-endian 64-bit integer**;
5. `bucket = unsigned64 mod 100000`;
6. select the first variation whose cumulative integer allocation exceeds the bucket.

Rollout allocations are integer units summing exactly to 100000.

For algorithm version 1, the selected subject attribute is a non-empty string used exactly as supplied: no coercion, trimming, case folding, or Unicode normalization. Flag keys and salts are validated newline-free canonical values. Missing, null, empty, or non-string rollout attributes skip rollout and follow the documented `MISSING_ROLLOUT_KEY` fallback in `docs/05_FLAG_EVALUATION_ENGINE.md`.

Java uses unsigned remainder for the 64-bit intermediate; JavaScript uses `BigInt`. Neither implementation converts the intermediate to a signed modulo or JavaScript `Number`.

## Consequences

- deterministic;
- portable;
- easy to golden-test;
- stable if salt/subject/key stay fixed.

Changing rollout salt can reshuffle users and therefore requires deliberate UX/audit.

Expected hash outputs must be produced by verified code and frozen as golden fixtures, not guessed in documentation.
