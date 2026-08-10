# 08 - Frontend and UX Specification

## 1. Product goal

The React console should let a developer understand and safely change a production flag without reading documentation first.

The UI is an operations console, not a marketing site.

## 2. Technology

Baseline:

- React 19.2.x;
- TypeScript strict mode;
- Vite;
- React Router;
- TanStack Query for server state;
- a focused form/schema validation library selected when the first M6 form requires it;
- Playwright for browser acceptance tests.

Avoid a large state-management framework unless a concrete need appears. Most persistent state is server state.

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
            └── Analytics (later)
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
