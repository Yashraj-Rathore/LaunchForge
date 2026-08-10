# 09 - Security and Privacy

## 1. Security posture

LaunchForge changes production application behavior. Treat configuration writes and SDK credential handling as security-sensitive even though the product is not a secrets manager.

Primary threats:

- cross-tenant data access;
- unauthorized production flag changes;
- leaked SDK keys;
- replay/stale writes;
- rule/snapshot tampering;
- secret leakage in logs;
- browser token theft;
- denial of service against config distribution;
- malicious attribute values;
- supply-chain compromise.

## 2. Trust boundaries

```text
Operator Browser
   | OIDC/session
   v
Management API
   | trusted DB credentials
   v
PostgreSQL

Customer Server
   | server SDK key
   v
Config Edge

Customer Browser
   | public client key
   v
Config Edge

Internal
Management/Outbox -> Kafka -> Projector -> Redis -> Edge
```

Never treat organization/project/environment IDs supplied by a client as sufficient authorization.

## 3. Identity and operator authentication

Use OIDC Authorization Code flow through a same-origin backend-for-frontend/session design.

Local reference provider: Keycloak.

Requirements:

- PKCE where applicable;
- state/nonce validation;
- secure HttpOnly SameSite cookies;
- session rotation;
- bounded idle/absolute lifetime;
- logout invalidation;
- CSRF protection;
- no long-lived access/refresh tokens in browser localStorage.

M1 session baseline:

- identify an operator by the exact OIDC `(issuer, subject)` pair;
- keep OIDC tokens and session state server-side in PostgreSQL-backed Spring Session so logout/revocation and later multi-node control APIs have one authoritative session store;
- production cookie is `__Host-launchforge_session` with `Secure`, `HttpOnly`, `Path=/`, no `Domain`, and `SameSite=Lax`;
- rotate the session identifier after login and any privilege change;
- default idle lifetime is 30 minutes and absolute lifetime is 12 hours, both configurable only through bounded server configuration;
- local HTTP development uses a separate non-`__Host-` cookie name and never weakens non-local profiles.

`GET /api/v1/auth/csrf` returns a CSRF token bound to the authenticated session. Browser mutations send it in `X-CSRF-TOKEN`; it is kept in memory, not localStorage, and rotates with the session. Management endpoints are same-origin and do not enable cross-origin credentialed CORS.

M1 re-verified and pinned Keycloak `26.7.0` as the local reference provider. Compose uses `quay.io/keycloak/keycloak:26.7.0@sha256:0f198be292568439d700cdbfb893e69a6009bb43a94a06a945b1d3d506c76b13` only through the optional `identity` profile. The imported client is public, permits only the local callback origin, and requires PKCE S256. Fictional user passwords are supplied through a Keycloak realm-import environment placeholder at runtime and the secret value is absent from the realm file; no post-start bootstrap-administrator operation is required.

## 4. Authorization

Roles:

- Organization Owner;
- Admin;
- Developer;
- Viewer.

Suggested abilities:

| Ability | Owner | Admin | Developer | Viewer |
|---|---:|---:|---:|---:|
| View projects/flags | yes | yes | yes | yes |
| Edit draft flags | yes | yes | yes | no |
| Publish non-prod | yes | yes | yes | no |
| Publish production | yes | yes | no | no |
| Manage members | yes | yes | no | no |
| Manage SDK keys | yes | yes | constrained | no |
| Delete organization | yes | no | no | no |

Every application service and persistence path enforces tenant scope.

Developer production publishing is not configurable in the MVP. Adding delegated production-publish policy requires an explicit later issue with a persisted policy model, authorization tests, audit events, and safe defaults.

## 5. Multi-tenancy

All tenant-owned tables include `organization_id` directly or through an enforced parent ownership chain.

Defense in depth:

- server-derived organization context;
- scoped repository/query methods;
- compound foreign keys where useful;
- integration tests for cross-tenant read/write denial;
- avoid accepting `organizationId` as an authoritative mutation field;
- audit authorization denials without sensitive payloads.

Optional PostgreSQL row-level security may be evaluated later, but application tests remain required either way.

## 6. SDK keys

Types:

### Server SDK key

- full environment snapshot access for one environment;
- used only in trusted server environments;
- never bundled into browser/mobile code.

### Client/mobile key

- intentionally public identifier with reduced snapshot visibility;
- can fetch only browser-safe configuration;
- subject to stricter rate limits;
- cannot mutate anything.

### Management/service key

A future service-account credential for automation. Not required for MVP.

Storage:

- generate server-key lookup IDs with at least 96 bits and secret segments with 256 bits of cryptographically secure randomness, encoded base64url without padding;
- use the format `lf_srv_<lookup_id>_<secret>` for server keys and store only the lookup ID plus `HMAC-SHA-256(server_pepper, UTF8(secret))` verifier;
- store the pepper outside PostgreSQL in the deployment secret manager, record a non-secret pepper version with each verifier, and allow bounded overlap during pepper rotation;
- keep a non-secret prefix/fingerprint for lookup/display;
- display secret once;
- replacement server keys become active immediately; the old key has a configurable overlap capped at 24 hours and may be revoked immediately;
- snapshot requests check lifecycle state on every authentication; active streams revalidate no less frequently than every 60 seconds and disconnect revoked/expired keys;
- coalesce/batch last-used updates so one key writes at most once per hour by default.

High-entropy SDK keys are not passwords; do not add a deliberately slow password hash solely for their verifier. HMAC verification uses constant-time comparison. A browser/client key is a public opaque identifier, not a secret authenticator, and receives only the reduced browser projection.

## 7. Key lookup

Do not scan all key hashes.

Use a structured key format such as:

```text
lf_srv_<public_lookup_id>_<secret>
```

Store:

- public lookup ID;
- verifier of secret;
- environment/scope;
- lifecycle state.

Authenticate with constant-time secret verification.

Changing this version-1 format requires a credential-format version and migration/overlap plan. Key material must not embed tenant-sensitive metadata.

## 8. Configuration integrity

Each immutable revision has:

- monotonic environment revision number;
- canonical serialized content;
- checksum;
- author/reason;
- created timestamp.

Edge materialization verifies expected revision/checksum before serving.

SDKs validate schema/checksum before activation.

## 9. Input validation

Validate:

- flag key format and length;
- variation type/value;
- rule attributes/operators/value counts;
- context size;
- attribute key length;
- snapshot size;
- request body size;
- key header length;
- pagination bounds.

No arbitrary regex in MVP. No expression evaluation. No scripts.

## 10. Context privacy

Evaluation context can contain application-level attributes such as country or plan. The SDK evaluator needs those locally.

By default:

- server-side local evaluation does not send context to LaunchForge;
- snapshot fetch has no end-user context;
- analytics is opt-in;
- analytics payload uses only bounded fields required for aggregate measurement;
- support private attributes or exclusion lists before analytics is enabled.

Never log full evaluation context.

## 11. Browser-safe configuration

Browser SDK clients can inspect anything delivered to them.

Therefore:

- mark environments/flags or rules that are server-only;
- do not include sensitive variation values in browser snapshots;
- do not rely on hidden client-side flags for authorization;
- server application authorization must not depend solely on a client-side flag.

Feature flags alter behavior; they are not an access-control boundary.

## 12. CSRF, CORS, and headers

Management UI:

- same-origin recommended;
- CSRF token for state-changing requests;
- restrictive CORS;
- HSTS outside local;
- CSP;
- `X-Content-Type-Options: nosniff`;
- appropriate `Referrer-Policy`;
- frame restrictions.

Config Edge:

- server SDK endpoints generally do not need permissive browser CORS;
- browser SDK endpoint CORS is explicitly configured and key type restricted;
- credentials are not accepted from arbitrary origins.

Browser SDK origins are an explicit exact-origin allowlist per key/environment, use no credentialed CORS, and never use `*` for a production browser projection. Origin checks and CORS are abuse controls, not authentication or confidentiality; the public key and every delivered browser-visible value remain inspectable by end users.

## 13. Rate limiting

Independent policies:

- login/session endpoints;
- management mutations;
- SDK snapshot reads;
- SSE connections;
- analytics ingestion;
- key creation/rotation.

Partition on trusted authenticated identity where available, otherwise cautiously use IP plus key lookup identifiers.

Rate limiting does not replace authentication or request-size limits.

## 14. Audit

Immutable append-oriented audit events for:

- organization/member lifecycle;
- project/environment lifecycle;
- flag creation/update;
- publish;
- rollback;
- SDK key creation/rotation/revocation;
- production policy changes.

Audit contains:

- actor;
- action;
- target type/id;
- organization;
- timestamp;
- correlation/trace identifier;
- safe structured before/after summary;
- reason/reference where relevant.

Never audit plaintext SDK keys, tokens, or complete OIDC claims.

## 15. Logging

Structured logs must not contain:

- credentials;
- bearer/cookie values;
- SDK secret;
- complete config snapshot unless explicitly safe/test-only;
- arbitrary context attributes;
- email addresses unless strictly needed and redacted;
- raw request bodies.

Add automated log-redaction tests for key flows.

## 16. Dependency and supply-chain security

CI should include:

- Maven dependency review/vulnerability scan;
- npm lockfile audit policy;
- secret scan;
- container scan;
- pinned GitHub Actions SHAs;
- SBOM generation;
- provenance/attestation for release images;
- immutable image digest promotion.

Do not auto-upgrade major dependencies without tests.

## 17. Threat-model cases

At minimum test/document:

1. attacker changes organization ID in path/body;
2. stolen/revoked SDK key;
3. client key used against server endpoint;
4. replayed stale publish request;
5. malicious huge context;
6. invalid Unicode/serialization;
7. forged stream request;
8. SSE connection exhaustion;
9. Redis poisoning/stale projection;
10. Kafka duplicate/replay;
11. operator stale-write conflict;
12. compromised browser cannot retrieve server SDK key.

## 18. Secret management

Local:

- `.env` not committed;
- safe examples in `templates/`.

Staging/production:

- cloud secret manager or Kubernetes external secret integration;
- least-privilege workload identity;
- no secrets in container image, Helm values committed to Git, or GitHub workflow source.

## 19. Retention

Define separate policies for:

- operator audit;
- optional analytics events;
- session records;
- key metadata.

Configuration revision history should be long-lived because it supports rollback/audit. Retention jobs require dry-run/preview and explicit documentation before destructive behavior.

## 20. Security definition of done

A release is not production-ready until:

- cross-tenant tests pass;
- auth and role tests pass;
- key revocation is demonstrated;
- secret scanning passes;
- no critical/high unaccepted image findings;
- log privacy tests pass;
- production headers configured;
- rollback path tested;
- documented threat model reviewed.
