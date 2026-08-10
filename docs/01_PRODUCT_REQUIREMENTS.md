# 01 — Product Requirements

## 1. Product summary

LaunchForge is a multi-tenant developer platform for feature flags and remote configuration. Teams change application behavior without redeploying by publishing versioned configuration delivered to SDKs and evaluated locally.

## 2. Primary users

### Developer
Integrates an SDK, evaluates flags safely, debugs evaluation reasons, and tests Development/Staging before Production.

### Engineering lead / release manager
Needs gradual rollout, kill switches, auditability, rollback, and confidence that application request paths do not depend on LaunchForge uptime.

### Organization owner/admin
Needs memberships, environments, roles, SDK-key rotation, audit history and operational visibility.

### Viewer/support engineer
Needs read-only access to current configuration, revision history and evaluation diagnostics.

## 3. Core jobs to be done

1. Create organization, project and environments.
2. Create a typed flag with safe defaults.
3. Configure environments differently.
4. Target a cohort with deterministic rules.
5. Gradually roll out to a stable percentage.
6. Publish/distribute a revision.
7. Observe SDK behavior change without app redeploy.
8. Disable a feature quickly.
9. Roll back to prior known-good behavior while preserving history.
10. Explain why a subject received a variation.
11. Rotate/revoke SDK credentials.
12. Optionally observe privacy-minimized evaluation counts.

## 4. Flag types

MVP:

- boolean
- string
- number
- JSON value with size/depth limits

A flag type is immutable after creation.

## 5. Variations

A flag has 2–10 named variations:

- stable variation ID
- human label
- typed value
- optional description

Each environment configuration defines:

- enabled state
- off variation
- default/on variation
- ordered targeting rules
- optional percentage rollout
- rollout salt
- concurrency version

## 6. Environments

Projects start with Development, Staging and Production. Names may change; keys are immutable.

Production publish/rollback requires a human change reason. The UI must make the active environment unmistakable.

## 7. Targeting

Rules are ordered. Each rule contains ANDed conditions. First matching rule wins.

Supported attribute categories:

- string
- number
- boolean
- semantic version

Algorithm version 1 accepts scalar context attributes only. `IN` and `NOT_IN` compare one string context value with a configured list of strings. List-valued context attributes are deferred until a later algorithm version defines their exact semantics.

No arbitrary JavaScript, SpEL, SQL, user-provided regex, or dynamically executed code.

## 8. Percentage rollout

Rollouts are:

- deterministic
- stable across restarts
- identical across Java and TypeScript SDKs
- independent of request order
- re-randomized only through explicit salt regeneration

Weights total 100,000 buckets for 0.001% resolution.

## 9. Publishing and revisions

Management edits are not runtime-visible until publish.

Publish must atomically:

1. validate the complete environment configuration;
2. create an immutable environment snapshot;
3. assign a strictly increasing environment revision;
4. persist audit metadata;
5. insert an outbox event in the same transaction;
6. return the durable revision.

Distribution to every SDK is eventually consistent and occurs after commit.

Rollback never rewinds revision history. It publishes prior content as a **new higher revision**.

## 10. SDK requirements

SDKs:

- bootstrap a versioned snapshot;
- cache last-known-good;
- evaluate locally;
- expose caller defaults;
- optionally subscribe to revision stream;
- fall back to polling with jitter;
- never block startup forever;
- never call LaunchForge for each flag evaluation;
- expose evaluation detail/reason;
- allow complete analytics disablement.

## 11. Audit

Append-only audit records include:

- actor ID/type
- organization/project/environment
- action type
- resource identity
- old/new revision references where relevant
- human Production reason
- UTC timestamp
- correlation ID

Do not record secrets or arbitrary targeting context.

## 12. Commercially testable MVP

Requires:

- self-hosted Docker Compose
- Java and React integration guides
- organization/project/environment management
- typed flags/rules/rollout
- Java SDK
- JavaScript/React SDK
- live update demo
- audit/revision history
- SDK key lifecycle
- polished admin UI
- one-command fictional demo

Analytics, experiments, SAML/SCIM and multi-region are not required for the first pilot.

## 13. Success evidence

### Engineering

- cross-SDK conformance suite passes;
- cross-organization access tests pass;
- duplicate/stale config event has no harmful effect;
- SDK continues last-known-good during LaunchForge outage;
- kill switch reaches connected demo without redeploy;
- rollback produces a newer revision restoring old behavior;
- benchmark methodology/results are reproducible.

### Portfolio

A recruiter can understand the problem, architecture and Java/Spring focus within one minute from README media.

### Commercial

Do not call the product validated until external teams actually integrate and repeatedly use it. Stars/page views are not product validation.

## 14. Non-functional priority order

1. deterministic correctness
2. safe customer behavior during outage
3. tenant isolation and credential safety
4. local evaluation performance
5. reliable propagation
6. operability/debuggability
7. scale
8. breadth
