# Frontend Workspace

`admin-web` contains the React/strict-TypeScript/Vite shell initialized in LF-0004. M1 adds the minimal OIDC sign-in/sign-out state and server-authorized organization/role display; broader product screens remain deferred to M6.

The development proxy forwards the original browser origin for `/api`, `/login`, and `/oauth2`, preserving the same-origin BFF callback through `http://localhost:5173`. The shell stores no OIDC token in browser storage.

Product UX is specified in `docs/08_FRONTEND_UX.md`.
