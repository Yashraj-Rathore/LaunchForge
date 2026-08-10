# Contracts

This directory is reserved for versioned language-neutral contracts created during implementation.

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
