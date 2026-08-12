# Contracts

This directory contains versioned language-neutral contracts created during implementation.

Expected artifacts include:

```text
config-snapshot.schema.json
analytics-event.schema.json
management-openapi.yaml
golden-vectors/
```

Rules:

- contracts are versioned;
- SDK snapshot semantics cannot change silently;
- Java and JavaScript consume the same golden corpus;
- event/snapshot incompatible changes require explicit versioning;
- generated artifacts identify their source when applicable.

Do not add speculative schema files before their owning backlog issue.

## Evaluator version 1

`golden-vectors/evaluator-v1.json` is the canonical cross-SDK evaluator corpus from LF-0302. It contains generated SHA-256 rollout buckets, exact allocation-boundary subjects, a deterministic 10,000-subject sample, every algorithm-version-1 operator, typed evaluation cases, malformed snapshots, and its own checksum over the RFC 8785 projection without `corpusChecksum`.

The executable generator and verifier are in the Java SDK test source. Regenerate and immediately verify the frozen artifact from the repository root:

```powershell
./mvnw.cmd -pl sdks/java/launchforge-java-sdk -am test "-Dtest=GoldenVectorCorpusTest" "-Dlaunchforge.updateGoldenVectors=true"
./mvnw.cmd -pl sdks/java/launchforge-java-sdk -am test "-Dtest=GoldenVectorCorpusTest"
corepack pnpm --filter @launchforge/js-core test
```

Never manually edit expected cryptographic outputs. Any semantic change requires an explicit algorithm/schema decision and both Java and JavaScript suites to pass this same corpus. CI runs both gates together in the `evaluator-compatibility` job.
