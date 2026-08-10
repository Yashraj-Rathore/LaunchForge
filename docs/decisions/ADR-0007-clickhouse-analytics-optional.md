# ADR-0007 - ClickHouse Only for Optional Evaluation Analytics

- Status: Accepted for M8, deferred until needed
- Date: 2026-08-10

## Context

High-volume evaluation/exposure events are analytical/time-series-like and may dwarf control-plane traffic. PostgreSQL is optimized here for transactional configuration, not arbitrary event analytics.

## Decision

If analytics M8 is implemented, use ClickHouse for bounded evaluation-event storage and aggregate queries.

Analytics is opt-in and isolated. Configuration publish, snapshot delivery, SDK refresh and local evaluation must work when ClickHouse is unavailable.

## Consequences

- better analytical fit;
- adds operational complexity;
- requires privacy/retention decisions;
- not started until core product is already useful.

This component may be skipped without invalidating the main portfolio project.
