# 22 - Security Hardening Review

Review date: 2026-08-17

Scope: LF-0901 through LF-0906

Disposition: no unresolved critical/high finding; ranked residual risks remain below.

## Review method

This review traced the twelve cases in `docs/09_SECURITY_PRIVACY.md` through the domain,
application, persistence, HTTP, SDK, and distribution boundaries. Evidence is executable unless
explicitly identified as an operational/release check. It does not claim a penetration test or an
external audit.

## Threat assessment and evidence

| # | Threat | M9 disposition | Primary evidence |
|---|---|---|---|
| 1 | Organization ID changed in path/body/filter | Mitigated: access comes from authenticated membership; unknown ownership body fields fail; resource filters are independently scoped and cross-tenant IDs are not-found. | `ControlPlaneService.auditHistory`; `ControlPlanePostgresIT.auditExportRetentionAndTamperControlsStayTenantScoped`; `managementBodyManipulationAndOversizedPayloadsAreRejectedWithSecurityHeaders` |
| 2 | Stolen/revoked server SDK key | Mitigated: high-entropy one-time key, lookup plus versioned HMAC verifier, constant-time compare, immediate snapshot check, one-poll stream revalidation, rotate/revoke audit. | `ServerSdkKeyCredentialTest`; `SdkAuthenticationServiceTest`; `DistributionPipelineIT.revokedKeyCannotFetchAndActiveStreamDisconnects` |
| 3 | Browser key used against server endpoint | Mitigated: disjoint credential formats/authentication filters and projections. | `ConfigEdgeHttpContractTest.browserCredentialClassCannotAuthenticateTheServerSnapshotEndpoint`; `BrowserConfigEdgeHttpContractTest.disallowedOriginAndUnknownClientKeyAreDenied` |
| 4 | Replayed stale publish | Mitigated by ETag/`If-Match`, row locks, optimistic versions, and strictly increasing immutable revision numbers. | `ControlPlanePostgresIT.concurrentDraftWritesAndPublishesRejectStaleVersions`; `browserApiRequiresCsrfAndIfMatchAndRejectsTypedVariationMismatch` |
| 5 | Malicious huge context/body | Mitigated by domain cardinality/text limits, 1 MiB management body cap, 256 KiB reactive codec/analytics cap, finite analytics batch/concurrency limits. | `RequestPrivacyContractTest`; `EdgeWebSecurityContractTest`; M6 simulator tests |
| 6 | Invalid Unicode/serialization | Mitigated by I-JSON validation, strict duplicate handling, canonical snapshot encoding, checksum verification, and cross-SDK golden vectors. | `JacksonSnapshotCodecTest`; `GoldenVectorCorpusTest`; snapshot integrity tests |
| 7 | Forged stream request | Mitigated by exact credential-class filters before admission, environment scope from stored key, lifecycle revalidation, and revision-only payload. | `RevisionStreamControllerTest`; `SdkAuthenticationServiceTest` |
| 8 | SSE connection exhaustion | Mitigated by stream-start rate limits, local global/per-key bounds, Redis atomic global/per-key leases, renew/expiry/release behavior, and polling/LKG fallback. | `StreamConnectionLimiterTest`; `EdgeRateLimiterTest`; `EdgeWebSecurityContractTest.trustedKeyRateLimitReturnsStable429AndRetryAfter` |
| 9 | Redis poisoning/stale projection | Mitigated by revision monotonicity, checksum verification, PostgreSQL authority/fallback, and reconciliation rebuild. | `RedisBackedEdgeRepositoryTest`; `DistributionPipelineIT` |
| 10 | Kafka duplicate/replay | Mitigated by versioned keyed events, idempotent consumers, and revision ordering authority. | `DistributionPipelineIT` duplicate/stale-event cases |
| 11 | Operator stale-write conflict | Mitigated by required preconditions, optimistic version checks, `409` contract, and preserved local UI edits. | `ControlPlanePostgresIT`; M6 console Playwright stale-write coverage |
| 12 | Compromised browser retrieves server-only key/snapshot | Mitigated by same-origin OIDC management auth, distinct public key class, exact-origin non-credentialed CORS, and pre-checksum client-visible projection. | `BrowserConfigEdgeHttpContractTest.exactAllowedOriginReceivesOnlyClientVisibleFlagsAndProjectionChecksum`; `ConfigEdgePostgresIT.browserProjectionNeverReturnsServerOnlyFlags` |

Additional M9 evidence covers immutable/tenant-scoped audit retention and export, fake-secret log
capture, metric-label privacy, HTTP header policy, request limits, unknown pepper versions, and
stable `429` plus `Retry-After` responses.

## Ranked residual risks

| Rank | Risk | Current decision | Follow-up boundary |
|---|---|---|---|
| Medium | During Redis outage, fixed-window and active-SSE limits fall back per process, so aggregate cluster allowance can exceed the normal global ceiling. | Accepted to preserve configuration availability; local caps never disappear and fallback is metered/runbooked. | Exercise reconnect/abuse capacity in LF-1004 and alert on fallback in LF-1002. |
| Medium | Audit retention is an operator-driven bounded batch, not a legal-hold/archive/scheduled-retention product. | Deletion stays disabled by default; preview/export/approval are mandatory. | Any legal hold, signed archive, scheduler, or policy automation requires a separately approved issue. |
| Low | Server-key pepper material is operator-managed; losing an old active pepper version fails those keys closed. | Versioned pepper map and unknown-version failure are intentional. | Document/automate secret-manager rotation with deployment work; do not silently substitute a pepper. |
| Low | CSV export is deliberately capped at 200 newest matching events per request. | Accepted as a safe operational export, not bulk compliance archival. | Pagination/bulk jobs require a separate bounded contract and issue. |

No code fix outside LF-0901 through LF-0906 was introduced for these residual items. The M10 links
above identify existing explicit issue boundaries; remaining product expansions are not implied
backlog commitments.

## Release decision checklist

The normative release checklist is in `docs/12_DEVOPS_CICD.md`. A release reviewer must attach the
exact command results, scan artifacts, threat-review acknowledgement, retention-deletion state, and
rollback target. Any new critical/high finding blocks release unless explicitly risk-accepted by the
responsible owner with scope, expiry, and remediation issue.
