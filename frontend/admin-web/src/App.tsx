import { QueryClient, QueryClientProvider, useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom';
import { api, ApiError } from './api';
import { Icon } from './Icon';
import { AuditPage } from './pages/AuditPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { FlagEditorPage, FlagsPage } from './pages/FlagsPage';
import { KeysPage } from './pages/KeysPage';
import { RevisionsPage } from './pages/RevisionsPage';
import { ConsoleShell } from './workspace';
import type { Environment, Project, Role, Session } from './types';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, staleTime: 10_000 },
    mutations: { retry: false },
  },
});

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <SessionBoundary />
      </BrowserRouter>
    </QueryClientProvider>
  );
}

function SessionBoundary() {
  const session = useQuery({ queryKey: ['session'], queryFn: api.session });
  if (session.isPending)
    return <StatusPanel title="Opening the control plane" detail="Checking your secure session…" />;
  if (session.error instanceof ApiError && session.error.status === 401) return <Login />;
  if (session.isError) {
    return (
      <StatusPanel
        kind="error"
        title="Session unavailable"
        detail="The control plane could not load your authenticated session."
        action={<button onClick={() => void session.refetch()}>Try again</button>}
      />
    );
  }
  if (session.data.organizations.length === 0) {
    return (
      <StatusPanel
        title="No organization access"
        detail="Ask an owner to add this identity to an organization."
      />
    );
  }
  return <AuthenticatedRoutes session={session.data} />;
}

function AuthenticatedRoutes({ session }: { readonly session: Session }) {
  const first = session.organizations[0];
  if (first === undefined) return null;
  return (
    <Routes>
      <Route path="/" element={<Navigate replace to={`/organizations/${first.id}`} />} />
      <Route
        path="/organizations/:organizationId"
        element={<OrganizationLanding session={session} />}
      />
      <Route
        path="/organizations/:organizationId/projects/:projectId"
        element={<ProjectLanding session={session} />}
      />
      <Route
        path="/organizations/:organizationId/projects/:projectId/environments/:environmentId"
        element={<ConsoleShell session={session} />}
      >
        <Route index element={<Navigate replace to="flags" />} />
        <Route path="flags" element={<FlagsPage />} />
        <Route path="flags/:flagId" element={<FlagEditorPage />} />
        <Route path="revisions" element={<RevisionsPage />} />
        <Route path="keys" element={<KeysPage />} />
        <Route path="audit" element={<AuditPage />} />
        <Route path="analytics" element={<AnalyticsPage />} />
      </Route>
      <Route path="*" element={<Navigate replace to="/" />} />
    </Routes>
  );
}

function OrganizationLanding({ session }: { readonly session: Session }) {
  const { organizationId = '' } = useParams();
  const organization = session.organizations.find((candidate) => candidate.id === organizationId);
  const projects = useQuery({
    queryKey: ['projects', organizationId],
    queryFn: () => api.projects(organizationId),
    enabled: organization !== undefined,
  });
  if (organization === undefined) return <Denied />;
  if (projects.isPending)
    return <StatusPanel title="Loading projects" detail={`Scoping ${organization.name}…`} />;
  if (projects.isError)
    return (
      <QueryError title="Projects unavailable" error={projects.error} retry={projects.refetch} />
    );
  const first = projects.data.find((project) => project.status === 'ACTIVE');
  if (first !== undefined) {
    return <Navigate replace to={`/organizations/${organizationId}/projects/${first.id}`} />;
  }
  return (
    <CreateProject
      organizationId={organizationId}
      organizationName={organization.name}
      role={organization.role}
    />
  );
}

function ProjectLanding({ session }: { readonly session: Session }) {
  const { organizationId = '', projectId = '' } = useParams();
  const organization = session.organizations.find((candidate) => candidate.id === organizationId);
  const projects = useQuery({
    queryKey: ['projects', organizationId],
    queryFn: () => api.projects(organizationId),
    enabled: organization !== undefined,
  });
  const environments = useQuery({
    queryKey: ['environments', projectId],
    queryFn: () => api.environments(projectId),
    enabled: projects.data?.some((project) => project.id === projectId) ?? false,
  });
  if (organization === undefined) return <Denied />;
  if (projects.isPending || environments.isPending)
    return <StatusPanel title="Loading environments" detail="Resolving project context…" />;
  if (projects.isError)
    return (
      <QueryError title="Project unavailable" error={projects.error} retry={projects.refetch} />
    );
  const project = projects.data.find((candidate) => candidate.id === projectId);
  if (project === undefined) return <Denied />;
  if (environments.isError)
    return (
      <QueryError
        title="Environments unavailable"
        error={environments.error}
        retry={environments.refetch}
      />
    );
  const first = environments.data.find((environment) => environment.status === 'ACTIVE');
  if (first !== undefined) {
    return (
      <Navigate
        replace
        to={`/organizations/${organizationId}/projects/${projectId}/environments/${first.id}/flags`}
      />
    );
  }
  return <CreateEnvironment project={project} role={organization.role} />;
}

function CreateProject({
  organizationId,
  organizationName,
  role,
}: {
  readonly organizationId: string;
  readonly organizationName: string;
  readonly role: Role;
}) {
  const navigate = useNavigate();
  const [key, setKey] = useState('storefront');
  const [name, setName] = useState('Storefront');
  const mutation = useMutation({
    mutationFn: () => api.createProject(organizationId, { key, name, description: '' }),
    onSuccess: async (project) => {
      await queryClient.invalidateQueries({ queryKey: ['projects', organizationId] });
      void navigate(`/organizations/${organizationId}/projects/${project.id}`);
    },
  });
  if (!canEdit(role)) {
    return (
      <StatusPanel
        title="No projects yet"
        detail="Your role is read-only. Ask an owner or admin to create the first project."
      />
    );
  }
  return (
    <ResourceSetup title={`Create the first project for ${organizationName}`}>
      <label>
        Project key
        <input value={key} onChange={(event) => setKey(event.target.value)} />
      </label>
      <label>
        Display name
        <input value={name} onChange={(event) => setName(event.target.value)} />
      </label>
      <button disabled={mutation.isPending} onClick={() => mutation.mutate()} type="button">
        Create project
      </button>
      <MutationError error={mutation.error} />
    </ResourceSetup>
  );
}

function CreateEnvironment({ project, role }: { readonly project: Project; readonly role: Role }) {
  const { organizationId = '' } = useParams();
  const navigate = useNavigate();
  const [key, setKey] = useState('development');
  const [name, setName] = useState('Development');
  const [kind, setKind] = useState<Environment['kind']>('DEVELOPMENT');
  const mutation = useMutation({
    mutationFn: () => api.createEnvironment(project.id, { key, name, kind }),
    onSuccess: async (environment) => {
      await queryClient.invalidateQueries({ queryKey: ['environments', project.id] });
      void navigate(
        `/organizations/${organizationId}/projects/${project.id}/environments/${environment.id}/flags`,
      );
    },
  });
  if (!canEdit(role)) {
    return (
      <StatusPanel
        title="No environments yet"
        detail="Your role is read-only. Ask an owner or admin to create the first environment."
      />
    );
  }
  return (
    <ResourceSetup title={`Create the first environment for ${project.name}`}>
      <label>
        Environment key
        <input value={key} onChange={(event) => setKey(event.target.value)} />
      </label>
      <label>
        Display name
        <input value={name} onChange={(event) => setName(event.target.value)} />
      </label>
      <label>
        Environment kind
        <select
          value={kind}
          onChange={(event) => setKind(event.target.value as Environment['kind'])}
        >
          <option value="DEVELOPMENT">Development</option>
          <option value="STAGING">Staging</option>
          <option value="PRODUCTION">Production</option>
          <option value="CUSTOM">Custom</option>
        </select>
      </label>
      <button disabled={mutation.isPending} onClick={() => mutation.mutate()} type="button">
        Create environment
      </button>
      <MutationError error={mutation.error} />
    </ResourceSetup>
  );
}

function Login() {
  return (
    <main className="auth-shell">
      <section aria-labelledby="login-heading" className="auth-card">
        <div className="auth-brand">
          <span className="brand-mark large" aria-hidden="true">
            <i />
            <i />
          </span>
          <span>
            <strong>LaunchForge</strong>
            <small>Control plane</small>
          </span>
        </div>
        <div className="auth-layout">
          <div className="auth-copy">
            <p className="eyebrow">Safe changes, delivered fast</p>
            <h1 id="login-heading">Ship confidently. Recover instantly.</h1>
            <p>
              Control feature delivery, review every change, and keep runtime evaluation local to
              your applications.
            </p>
            <a className="button primary auth-action" href="/oauth2/authorization/keycloak">
              <span>Sign in with OpenID Connect</span>
              <Icon name="chevron" size={18} />
            </a>
            <p className="auth-security">
              <Icon name="shield" size={16} />
              Tokens stay server-side in the same-origin session boundary.
            </p>
          </div>
          <div className="auth-proof" aria-label="Platform capabilities">
            <span className="auth-proof-label">Built for release control</span>
            <ul>
              <li>
                <Icon name="check" />
                <span>
                  <strong>Immutable revisions</strong>
                  Every publish has a durable history.
                </span>
              </li>
              <li>
                <Icon name="activity" />
                <span>
                  <strong>Local evaluation</strong>
                  Customer request paths stay independent.
                </span>
              </li>
              <li>
                <Icon name="revision" />
                <span>
                  <strong>Safe rollback</strong>
                  Restore known-good behavior without rewinding history.
                </span>
              </li>
            </ul>
          </div>
        </div>
      </section>
    </main>
  );
}

export function StatusPanel({
  title,
  detail,
  kind = 'neutral',
  action,
}: {
  readonly title: string;
  readonly detail: string;
  readonly kind?: 'neutral' | 'error';
  readonly action?: React.ReactNode;
}) {
  return (
    <main className="status-shell">
      <section className={`status-panel ${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
        <span className="status-icon">
          <Icon name={kind === 'error' ? 'alert' : 'activity'} />
        </span>
        <span className="status-kicker">LaunchForge</span>
        <h1>{title}</h1>
        <p>{detail}</p>
        {action}
      </section>
    </main>
  );
}

function QueryError({
  title,
  error,
  retry,
}: {
  readonly title: string;
  readonly error: Error;
  readonly retry: () => unknown;
}) {
  const denied = error instanceof ApiError && error.status === 403;
  return (
    <StatusPanel
      kind="error"
      title={denied ? 'Access denied' : title}
      detail={denied ? 'Your current role cannot open this resource.' : error.message}
      action={<button onClick={() => void retry()}>Retry</button>}
    />
  );
}

function Denied() {
  return (
    <StatusPanel
      kind="error"
      title="Access denied"
      detail="That route is outside your server-authorized organization context."
    />
  );
}

function ResourceSetup({
  title,
  children,
}: {
  readonly title: string;
  readonly children: React.ReactNode;
}) {
  return (
    <main className="status-shell">
      <section className="setup-card">
        <p className="eyebrow">Workspace setup</p>
        <h1>{title}</h1>
        <div className="form-grid">{children}</div>
      </section>
    </main>
  );
}

export function MutationError({ error }: { readonly error: Error | null }) {
  if (error === null) return null;
  const conflict = error instanceof ApiError && error.code === 'STALE_RESOURCE_VERSION';
  return (
    <p className="inline-error" role="alert">
      {conflict
        ? 'This resource changed on the server. Your local edits are preserved; reload and reconcile before saving.'
        : error.message}
    </p>
  );
}

export function canEdit(role: Role): boolean {
  return role !== 'VIEWER';
}

export function canPublish(role: Role, environment: Environment): boolean {
  return (
    role === 'OWNER' ||
    role === 'ADMIN' ||
    (role === 'DEVELOPER' && environment.kind !== 'PRODUCTION')
  );
}

export { queryClient };
