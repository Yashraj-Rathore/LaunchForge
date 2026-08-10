# Codex Prompt 04 - Java Evaluator and SDK

Implement **LF-0301 through LF-0307 only**.

This is a flagship milestone.

Build:

- pure Java evaluator core with no Spring/network/database dependency;
- algorithm version 1 exactly as `ADR-0003` and `docs/05_FLAG_EVALUATION_ENGINE.md`;
- verified language-neutral golden vector corpus;
- Java SDK bootstrap;
- atomic immutable snapshot activation;
- typed local evaluation/detail APIs;
- conditional polling with jitter;
- in-memory last-known-good behavior;
- fictional Spring Boot demo app.

Critical:

- do **not** manually invent SHA-256 expected outputs; generate with reference code and freeze/test them;
- no network access on evaluation hot path;
- no Kafka/Redis/SSE yet;
- SDK must not depend on Spring.

Add concurrency tests and all golden tests. Update docs/status/changelog and stop.
