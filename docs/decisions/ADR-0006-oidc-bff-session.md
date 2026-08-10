# ADR-0006 - OIDC Through a Same-Origin BFF/Session

- Status: Accepted
- Date: 2026-08-10

## Context

The React management console needs operator authentication. Keeping long-lived bearer/refresh tokens in browser storage increases exposure and complicates secure API use.

## Decision

Use OIDC Authorization Code flow with server-side/security-framework handling and a same-origin secure HttpOnly session cookie for the browser.

Keycloak is the local/reference identity provider. The architecture remains provider-neutral through OIDC.

State-changing requests use CSRF protection.

## Consequences

- tokens can remain server-side;
- same-origin API is simpler;
- server owns session lifecycle;
- requires session storage/validation strategy and CSRF;
- deployment must route UI/API appropriately.

The browser never receives a server SDK key.
