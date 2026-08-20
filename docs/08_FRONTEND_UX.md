# 08 - Frontend and UX Specification

## 1. Product goal

The React console should let a developer understand and safely change a production flag without reading documentation first.

The UI is an operations console, not a marketing site.

## 2. Technology

Baseline:

- React 19.2.x;
- TypeScript strict mode;
- Vite;
- React Router `7.18.2`;
- TanStack Query `5.101.4` for server state;
- Zod `4.4.3` for focused form/schema validation;
- Playwright for browser acceptance tests.

Avoid a large state-management framework unless a concrete need appears. Most persistent state is server state.

M6 uses URL routes as the selected organization/project/environment context, TanStack Query as the
only remote-state cache, component-local state for unsaved forms and one-time secrets, and Zod plus
explicit serializers at the HTTP boundary. No bearer token, SDK credential, or persistent draft is
stored in browser storage.

## 3. Information architecture

```text
Organization
  ├── Overview
  ├── Members
  └── Projects
       └── Project
            ├── Flags
            ├── Environments
            ├── SDK Keys
            ├── Revisions
            ├── Audit
            └── Analytics
```

Top-level environment context must always be visible on destructive or publish actions.

## 4. Core screens

### Login

- OIDC redirect via same-origin BFF/session;
- clear expired-session state;
- no access tokens displayed to JavaScript.

### Project dashboard

Show:

- environments;
- current published revisions;
- flag counts;
- recent publishes;
- edge/distribution status at a high level.

### Flag list

Columns/fields:

- key;
- display name;
- type;
- state in selected environment;
- rollout summary;
- last changed;
- tags.

Support search/filter without hiding environment context.

### Flag detail/editor

Sections:

1. identity and description;
2. variations;
3. environment state;
4. ordered targeting rules;
5. fallthrough;
6. percentage rollout;
7. change summary;
8. publish action.

Editing creates a draft. Runtime SDKs see only published revisions.

### Rule builder

The rule builder must:

- use explicit dropdown operators;
- make AND semantics obvious;
- prevent impossible type/operator combinations;
- allow rule reordering;
- support test/simulation before publish;
- never accept arbitrary executable expressions.

The console simulator calls `POST /api/v1/environments/{environmentId}/evaluate`. That endpoint invokes the same pure Java evaluator used by the Java SDK and the shared golden vectors. The console must not implement a third evaluator or infer a result from form state.

### Rollout editor

Represent percentages visually and numerically.

Requirements:

- total allocation must be exactly 100% / 100000 units;
- each variation has a stable key;
- salt changes require explicit warning because they reshuffle subjects;
- changing percentage must preview approximate impact;
- deterministic simulation accepts a sample context/subject.

### Revision history

Display:

- revision number;
- author;
- timestamp;
- change summary;
- reason/ticket reference when supplied;
- compare to previous;
- rollback action.

Rollback creates a new revision and UI must say so.

### Audit page

Filter by:

- actor;
- project/environment;
- action family;
- time range.

Do not expose secret values in audit output.

### SDK keys

Show:

- key name;
- type/scope;
- environment;
- prefix/fingerprint;
- created/last-used/revoked timestamps when available.

The secret is displayed only once on creation/rotation.

### Analytics

The environment-scoped M8 screen shows hour/day operational evaluation counts with time-range,
flag, and variation filters. It must always state that the data comes only from explicitly opted-in
SDKs and is not an experiment-significance or causal analysis. The screen discloses that no subject
identifier or raw targeting context is collected. Disabled/unavailable/empty analytics have distinct
states, and unavailable analytics explicitly says configuration delivery, publish, rollback, and
local evaluation are unaffected.

## 5. Publish workflow

Publishing production configuration is high impact.

Flow:

```text
Edit Draft
   -> Review Changes
   -> Simulation/Validation
   -> Enter reason
   -> Publish
   -> Success with revision N
```

For production environments, require explicit confirmation including project/environment and count of changed flags.

Do not use confirmation phrases intended to frustrate the user; make the impact clear.

## 6. Conflict handling

Use optimistic concurrency.

If another operator changes the draft/config while the user is editing:

- reject stale mutation with `409`;
- show the latest server version;
- preserve local edits when safe;
- require user reconciliation rather than silently overwriting.

## 7. Live status

The admin UI may receive SSE updates for operational status, but control writes remain normal authenticated HTTP mutations.

Useful live indicators:

- latest published revision;
- distribution/projection caught up;
- demo SDK connected;
- optional event ingestion status.

Never imply global propagation is complete based only on the browser receiving an SSE event.

## 8. Error states

Every screen must define:

- initial loading;
- empty state;
- authorization denial;
- validation errors;
- conflict;
- network error;
- server error;
- retry.

Never leave a production publish button active after an ambiguous failed request without reconciling server state.

## 9. Accessibility

Minimum:

- semantic landmarks;
- keyboard navigation;
- visible focus;
- labels for controls;
- 44px target sizing where practical;
- sufficient contrast;
- reduced-motion support;
- no color-only state communication;
- screen-reader announcement for publish result and important async errors.

Run automated accessibility checks plus targeted manual keyboard tests.

## 10. Responsive behavior

Primary target is developer laptops/desktops, but the console should remain usable on tablets and narrow windows.

Do not force dense flag-rule tables into unreadable horizontal layouts. Collapse into cards or stacked fields as needed.

## 11. Visual design

A clean technical operations style is preferred:

- restrained surfaces;
- strong environment badges;
- clear production warning treatment;
- monospace for flag keys/revision IDs;
- charts only when they aid a decision;
- no decorative animation that competes with operational state.

## 12. Frontend security

- same-origin API calls;
- HttpOnly secure session cookie;
- CSRF protection for mutations;
- no long-lived bearer token in localStorage;
- no server SDK key delivered to browser;
- output escaping by default;
- restrictive CSP established in hardening milestone.

## 13. Browser tests

Critical Playwright flows:

1. login;
2. create project/environment;
3. create flag and variations;
4. create targeting rule;
5. configure rollout;
6. simulate context;
7. publish;
8. see revision;
9. watch demo app update;
10. rollback and see new revision;
11. role-based denial;
12. stale edit conflict;
13. key create/rotate/revoke.

## 14. Demo experience

The recruiter demo should be reproducible with fictional data.

Suggested split-screen sequence:

1. DemoShop app on right uses LaunchForge React SDK.
2. Console on left shows `new-checkout`.
3. Change 0% -> 10%; show deterministic users differ.
4. Change country rule; show matching Canadian test user.
5. Set 100%; demo updates after stream notification/snapshot refresh.
6. Trigger kill switch; feature returns to old experience.
7. Show audit/revision history.
8. briefly show architecture/metrics.

The demo must not depend on a paid external service.

## 15. M6 implementation baseline

LF-0601 through LF-0606 are implemented in `frontend/admin-web`. The console includes:

- authenticated project/environment routing with persistent context and production treatment;
- typed flag/variation forms and visible draft-versus-published state;
- ordered, keyboard-operable rule and condition controls with type-specific operators;
- exact 100,000-unit rollouts, deliberate salt reseeding, and server-backed draft simulation;
- production-aware publish review, immutable history/diff, and rollback-as-new-revision messaging;
- separate server/browser SDK key views with one-time server-secret state; and
- safe tenant-scoped audit filtering.

Mutations use the same-origin CSRF token and the server's ETag. A stale response leaves local form
state mounted and directs the operator to reconcile. A failed publish or rollback invalidates the
environment query before a retry so the UI does not imply an uncertain write failed. The dedicated
Playwright journey covers typed creation, stale conflict, targeting, simulation, production publish,
key create/rotate/revoke, secret disappearance, audit output, and Viewer denial.

## 16. Responsive visual system baseline

The 2026-08-20 console refresh preserves the M6 operational workflows while establishing one
responsive, code-native visual system across the authenticated shell and management pages:

- wide screens use a persistent labeled navigation rail and compact workspace controls;
- intermediate screens collapse the rail without removing navigation destinations;
- tablet and phone widths use an accessible off-canvas navigation drawer, stacked workspace
  selectors, and full-width primary actions;
- dense metric, flag, analytics, audit, revision, and key content becomes cards or labeled stacked
  rows instead of requiring horizontal scrolling; and
- the smallest supported browser proof is a 390 x 844 viewport with no document-level horizontal
  overflow.

Flag discovery includes name/key search, type filtering, and a concise operational summary. The
flag editor exposes in-page navigation for definition, behavior, targeting, rollout, and simulation,
while retaining the existing optimistic-concurrency, draft, publish, and authorization boundaries.
Production environment identity and revision state remain visible before any management action.

The visual system uses semantic navigation and landmarks, visible keyboard focus, screen-reader
labels for icon-only controls, practical 44px targets, non-color status text, and reduced-motion
support. Motion is limited to short navigation and surface transitions and is removed when the
browser requests reduced motion.
