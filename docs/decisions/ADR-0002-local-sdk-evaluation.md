# ADR-0002 - Evaluate Feature Flags Locally in SDKs

- Status: Accepted
- Date: 2026-08-10

## Context

A remote evaluation API on every application request would add network latency and make customer availability depend directly on LaunchForge.

## Decision

SDKs fetch versioned environment snapshots and evaluate locally in memory.

Configuration refresh is asynchronous through polling and optional SSE revision notifications. SDKs retain a last-known-good snapshot.

## Consequences

Positive:

- very low evaluation latency;
- no network on hot path;
- customer application continues evaluating during LaunchForge outage;
- far fewer runtime requests.

Tradeoffs:

- clients may temporarily run older configuration;
- snapshot compatibility/security is important;
- cross-language evaluator semantics must be exact;
- propagation needs revision observability.

This ADR is central to the product architecture.
