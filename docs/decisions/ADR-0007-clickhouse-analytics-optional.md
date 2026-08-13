# ADR-0007 - ClickHouse Only for Optional Evaluation Analytics

- Status: Accepted and implemented in M8
- Date: 2026-08-10

## Context

High-volume evaluation/exposure events are analytical/time-series-like and may dwarf control-plane traffic. PostgreSQL is optimized here for transactional configuration, not arbitrary event analytics.

## Decision

M8 uses ClickHouse for bounded evaluation-event storage and aggregate queries. The official
`26.7.1.1315` image is tag-and-digest pinned in Compose and integration tests.

Analytics is opt-in and isolated. Configuration publish, snapshot delivery, SDK refresh and local evaluation must work when ClickHouse is unavailable.

## Consequences

- better analytical fit;
- adds operational complexity;
- requires privacy/retention decisions;
- introduced only after the core product and M7 distribution path were complete.

This component may be skipped without invalidating the main portfolio project.
