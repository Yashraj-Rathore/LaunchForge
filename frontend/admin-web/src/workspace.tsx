import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { NavLink, Outlet, useNavigate, useParams } from 'react-router-dom';
import { api, ApiError } from './api';
import { StatusPanel } from './App';
import { Icon } from './Icon';
import type { IconName } from './Icon';
import type { Environment, Organization, Project, Session } from './types';

export interface WorkspaceContext {
  readonly organization: Organization;
  readonly project: Project;
  readonly environment: Environment;
  readonly projects: readonly Project[];
  readonly environments: readonly Environment[];
}

const NAVIGATION: readonly {
  readonly path: string;
  readonly label: string;
  readonly icon: IconName;
}[] = [
  { path: 'flags', label: 'Flags', icon: 'flag' },
  { path: 'revisions', label: 'Revisions', icon: 'revision' },
  { path: 'keys', label: 'SDK keys', icon: 'key' },
  { path: 'audit', label: 'Audit', icon: 'audit' },
  { path: 'analytics', label: 'Analytics', icon: 'analytics' },
];

export function ConsoleShell({ session }: { readonly session: Session }) {
  const { organizationId = '', projectId = '', environmentId = '' } = useParams();
  const navigate = useNavigate();
  const [navigationOpen, setNavigationOpen] = useState(false);
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
  const initial = session.displayName.trim().charAt(0).toUpperCase() || 'U';
  return (
    <div className={`console ${production ? 'production-console' : ''}`}>
      <a className="skip-link" href="#console-content">
        Skip to content
      </a>
      {navigationOpen && (
        <button
          aria-label="Close navigation"
          className="navigation-scrim"
          onClick={() => setNavigationOpen(false)}
          type="button"
        />
      )}
      <aside
        aria-label="Primary navigation"
        className={`sidebar ${navigationOpen ? 'open' : ''}`}
        id="workspace-navigation"
      >
        <div className="sidebar-header">
          <div className="wordmark">
            <span className="brand-mark" aria-hidden="true">
              <i />
              <i />
            </span>
            <span className="brand-copy">
              <strong>LaunchForge</strong>
              <small>Control plane</small>
            </span>
          </div>
          <button
            aria-label="Close navigation"
            className="sidebar-close"
            onClick={() => setNavigationOpen(false)}
            type="button"
          >
            <Icon name="close" />
          </button>
        </div>
        <span className="nav-section-label">Workspace</span>
        <nav aria-label="Workspace pages">
          {NAVIGATION.map((item) => (
            <NavLink
              data-label={item.label}
              key={item.path}
              onClick={() => setNavigationOpen(false)}
              to={`${base}/${item.path}`}
            >
              <Icon name={item.icon} />
              <span>{item.label}</span>
              <Icon className="nav-chevron" name="chevron" size={16} />
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-scope" aria-label={`Current organization: ${organization.name}`}>
          <span className="scope-orb" aria-hidden="true">
            {organization.name.charAt(0).toUpperCase()}
          </span>
          <div>
            <small>Tenant scope</small>
            <strong>{organization.slug}</strong>
          </div>
        </div>
        <div className="sidebar-footer">
          <span className="user-avatar" aria-hidden="true">
            {initial}
          </span>
          <div className="user-copy">
            <strong>{session.displayName}</strong>
            <span>{organization.role}</span>
          </div>
          <button
            aria-label="Sign out"
            className="logout-button"
            onClick={() => void api.logout().then(() => window.location.assign('/'))}
            title="Sign out"
            type="button"
          >
            <Icon name="logout" />
          </button>
        </div>
      </aside>
      <div className="console-main">
        <header className="context-bar">
          <button
            aria-controls="workspace-navigation"
            aria-expanded={navigationOpen}
            aria-label="Open navigation"
            className="navigation-toggle"
            onClick={() => setNavigationOpen(true)}
            type="button"
          >
            <Icon name="menu" />
          </button>
          <div className="context-overview">
            <span className="context-label">Current workspace</span>
            <div>
              <strong>{project.name}</strong>
              <span aria-hidden="true">/</span>
              <span>{organization.name}</span>
            </div>
          </div>
          <div className="context-selectors">
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
          </div>
          <div className={`environment-badge ${production ? 'production' : ''}`}>
            <span className="environment-dot" aria-hidden="true" />
            <div>
              <span>{environment.kind}</span>
              <strong>{environment.name}</strong>
            </div>
            <small>Published revision {environment.currentRevision}</small>
          </div>
        </header>
        {production && (
          <div className="production-banner" role="status">
            <Icon name="alert" size={18} />
            <div>
              <strong>Production environment</strong>
              <span>Review impact and provide a reason before publishing.</span>
            </div>
          </div>
        )}
        <main id="console-content" className="content">
          <Outlet context={context} />
        </main>
      </div>
    </div>
  );
}
