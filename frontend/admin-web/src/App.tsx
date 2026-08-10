import { useEffect, useState } from 'react';

type Organization = {
  id: string;
  slug: string;
  name: string;
  role: 'OWNER' | 'ADMIN' | 'DEVELOPER' | 'VIEWER';
};

type Session = {
  subject: string;
  displayName: string;
  organizations: Organization[];
};

type ViewState =
  | { kind: 'loading' }
  | { kind: 'anonymous' }
  | { kind: 'authenticated'; session: Session }
  | { kind: 'error' };

async function loadSession(): Promise<ViewState> {
  const response = await fetch('/api/v1/auth/me', {
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });
  if (response.status === 401) {
    return { kind: 'anonymous' };
  }
  if (!response.ok) {
    return { kind: 'error' };
  }
  return { kind: 'authenticated', session: (await response.json()) as Session };
}

async function logout(): Promise<void> {
  const csrfResponse = await fetch('/api/v1/auth/csrf', {
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });
  if (!csrfResponse.ok) {
    throw new Error('Unable to obtain logout protection');
  }
  const csrf = (await csrfResponse.json()) as { headerName: string; token: string };
  const response = await fetch('/api/v1/auth/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { [csrf.headerName]: csrf.token },
  });
  if (!response.ok) {
    throw new Error('Logout failed');
  }
}

export function App() {
  const [view, setView] = useState<ViewState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    void loadSession()
      .then((nextView) => {
        if (active) setView(nextView);
      })
      .catch(() => {
        if (active) setView({ kind: 'error' });
      });
    return () => {
      active = false;
    };
  }, []);

  if (view.kind === 'loading') {
    return <main className="shell">Loading your secure session…</main>;
  }

  if (view.kind === 'anonymous') {
    return (
      <main className="shell">
        <section aria-labelledby="login-heading" className="identity-card">
          <p className="eyebrow">LaunchForge</p>
          <h1 id="login-heading">Sign in to the control plane</h1>
          <p>
            Authentication uses the configured OpenID Connect provider and a server-side session.
          </p>
          <a className="primary-action" href="/oauth2/authorization/keycloak">
            Sign in with OpenID Connect
          </a>
        </section>
      </main>
    );
  }

  if (view.kind === 'error') {
    return (
      <main className="shell">
        <section className="identity-card" role="alert">
          <h1>Session unavailable</h1>
          <p>The control plane could not load your authenticated session.</p>
          <button
            className="secondary-action"
            onClick={() => window.location.reload()}
            type="button"
          >
            Try again
          </button>
        </section>
      </main>
    );
  }

  const { session } = view;
  const organization = session.organizations[0];
  return (
    <main className="shell">
      <section aria-labelledby="console-heading" className="identity-card">
        <div className="identity-header">
          <div>
            <p className="eyebrow">Authenticated control plane</p>
            <h1 id="console-heading">Welcome, {session.displayName}</h1>
          </div>
          <button
            className="secondary-action"
            onClick={() => {
              void logout()
                .then(() => setView({ kind: 'anonymous' }))
                .catch(() => setView({ kind: 'error' }));
            }}
            type="button"
          >
            Sign out
          </button>
        </div>
        {organization ? (
          <dl className="organization-summary">
            <div>
              <dt>Organization</dt>
              <dd>{organization.name}</dd>
            </div>
            <div>
              <dt>Role</dt>
              <dd>{organization.role}</dd>
            </div>
            <div>
              <dt>Scope</dt>
              <dd>{organization.slug}</dd>
            </div>
          </dl>
        ) : (
          <p role="status">Your identity is valid, but it has no organization membership.</p>
        )}
        <p className="milestone-note">
          Tenancy and identity are ready. Flag and project management begin in later milestones.
        </p>
      </section>
    </main>
  );
}
