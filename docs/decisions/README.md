# Architecture Decision Records

ADRs record decisions whose rationale matters across milestones.

Current planning ADRs:

- ADR-0001 control plane/data plane;
- ADR-0002 local SDK evaluation;
- ADR-0003 deterministic rollout;
- ADR-0004 transactional outbox/Kafka;
- ADR-0005 Redis rebuildable materialization;
- ADR-0006 OIDC BFF/session;
- ADR-0007 optional ClickHouse analytics;
- ADR-0008 modern Java/Spring baseline.

When implementation evidence changes a decision:

1. do not silently edit history;
2. supersede the ADR with a new ADR or clearly update status/rationale;
3. update affected source-of-truth docs and acceptance criteria.
