import { useQuery } from '@tanstack/react-query';
import { NavLink, Outlet, useNavigate, useParams } from 'react-router-dom';
import { api, ApiError } from './api';
import { StatusPanel } from './App';
import type { Environment, Organization, Project, Session } from './types';

export interface WorkspaceContext {
  readonly organization: Organization;
  readonly project: Project;
  readonly environment: Environment;
  readonly projects: readonly Project[];
  readonly environments: readonly Environment[];
}

export function ConsoleShell({ session }: { readonly session: Session }) {
  const { organizationId = '', projectId = '', environmentId = '' } = useParams();
  const navigate = useNavigate();
  const organization = session.organizations.find((candidate) => candidate.id === organizationId);
  const projects = useQuery({
    queryKey: ['projects', organizationId],
    queryFn: () => api.projects(organizationId),
    enabled: organization !== undefined,
  });
  const environments = useQuery({
    queryKey: ['environments', projectId],
    queryFn: () => api.environments(projectId),
    enabled: projects.data?.some((candidate) => candidate.id === projectId) ?? false,
  });
  if (organization === undefined)
    return (
      <StatusPanel
        kind="error"
        title="Access denied"
        detail="That organization is not present in your authenticated session."
      />
    );
  if (projects.isPending || environments.isPending)
    return (
      <StatusPanel title="Loading workspace" detail="Resolving project and environment context…" />
    );
  const error = projects.error ?? environments.error;
  if (error !== null)
    return (
      <StatusPanel
        kind="error"
        title={
          error instanceof ApiError && error.status === 403
            ? 'Access denied'
            : 'Workspace unavailable'
        }
        detail={error.message}
        action={
          <button
            onClick={() => {
              void projects.refetch();
              void environments.refetch();
            }}
          >
            Retry
          </button>
        }
      />
    );
  if (projects.data === undefined || environments.data === undefined) {
    return <StatusPanel title="Loading workspace" detail="Waiting for complete server data…" />;
  }
  const projectValues = projects.data;
  const environmentValues = environments.data;
  const project = projectValues.find((candidate) => candidate.id === projectId);
  const environment = environmentValues.find((candidate) => candidate.id === environmentId);
  if (project === undefined || environment === undefined)
    return (
      <StatusPanel
        kind="error"
        title="Access denied"
        detail="That project or environment is outside the server-authorized context."
      />
    );
  const base = `/organizations/${organization.id}/projects/${project.id}/environments/${environment.id}`;
  const production = environment.kind === 'PRODUCTION';
  const context: WorkspaceContext = {
    organization,
    project,
    environment,
    projects: projectValues,
    environments: environmentValues,
  };
  return (
    <div className={`console ${production ? 'production-console' : ''}`}>
      <a className="skip-link" href="#console-content">
        Skip to content
      </a>
      <aside className="sidebar">
        <div className="wordmark">
          <span>LF</span>
          <strong>LaunchForge</strong>
        </div>
        <nav aria-label="Workspace">
          <NavLink to={`${base}/flags`}>Flags</NavLink>
          <NavLink to={`${base}/revisions`}>Revisions</NavLink>
          <NavLink to={`${base}/keys`}>SDK keys</NavLink>
          <NavLink to={`${base}/audit`}>Audit</NavLink>
          <NavLink to={`${base}/analytics`}>Analytics</NavLink>
        </nav>
        <div className="sidebar-footer">
          <span>{session.displayName}</span>
          <span className="role-chip">{organization.role}</span>
          <button
            className="text-button"
            onClick={() => void api.logout().then(() => window.location.assign('/'))}
          >
            Sign out
          </button>
        </div>
      </aside>
      <div className="console-main">
        <header className="context-bar">
          <div>
            <span className="context-label">Organization</span>
            <strong>{organization.name}</strong>
          </div>
          <label>
            <span>Project</span>
            <select
              aria-label="Project context"
              value={project.id}
              onChange={(event) =>
                void navigate(`/organizations/${organization.id}/projects/${event.target.value}`)
              }
            >
              {projectValues.map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {candidate.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>Environment</span>
            <select
              aria-label="Environment context"
              value={environment.id}
              onChange={(event) =>
                void navigate(
                  `/organizations/${organization.id}/projects/${project.id}/environments/${event.target.value}/flags`,
                )
              }
            >
              {environmentValues.map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {candidate.name}
                </option>
              ))}
            </select>
          </label>
          <div className={`environment-badge ${production ? 'production' : ''}`}>
            <span>{environment.kind}</span>
            <strong>{environment.name}</strong>
            <small>Published revision {environment.currentRevision}</small>
          </div>
        </header>
        {production && (
          <div className="production-banner" role="status">
            <strong>Production environment</strong>
            <span>Changes require an authorized role, explicit review, and a human reason.</span>
          </div>
        )}
        <main id="console-content" className="content">
          <Outlet context={context} />
        </main>
      </div>
    </div>
  );
}
