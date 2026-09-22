# Implementation Prompt 10 - Security Hardening

Implement **LF-0901 through LF-0906 only**.

Harden:

- SDK key generation/lookup/rotation/revocation;
- distributed rate/connection/body limits;
- CORS/security headers/CSP;
- audit retention/export;
- log/metric privacy tests;
- documented threat model.

Explicitly test:

- cross-tenant path/body manipulation;
- wrong key type;
- revoked key;
- SSE abuse boundaries;
- fake secrets never appear in logs;
- browser cannot retrieve server-only snapshot/key.

Do not add unrelated product features.

Run security and regression validation, update docs/status/changelog, and stop.
