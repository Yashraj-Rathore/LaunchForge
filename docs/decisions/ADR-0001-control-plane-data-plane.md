# ADR-0001 - Separate Control Plane and Runtime Data Plane

- Status: Accepted for implementation plan
- Date: 2026-08-10

## Context

Management writes are low-volume, security-sensitive and transactional. SDK configuration reads are higher-volume, latency-sensitive, and may involve many long-lived SSE connections.

Putting both workloads into one deployment makes scaling and failure isolation harder.

## Decision

Use:

- a management/control-plane Spring Boot application for organizations, flags, revisions, audit, keys and publishing;
- a separate Spring Boot WebFlux Config Edge for SDK snapshot and stream traffic.

PostgreSQL remains the authoritative control-plane store. The data plane may use Redis materialization later.

## Consequences

Positive:

- independent scaling;
- simpler security boundaries;
- edge restart/degradation need not affect management writes;
- clearer interview/system-design story grounded in workload differences.

Cost:

- an extra deployable;
- snapshot contract/versioning becomes important;
- distribution consistency must be observable.

Do not split additional microservices without a similarly concrete workload reason.
