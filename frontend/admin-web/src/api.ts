import type {
  AuditEvent,
  AnalyticsResponse,
  BrowserKey,
  Draft,
  Environment,
  Flag,
  FlagType,
  IssuedServerKey,
  ProblemDetails,
  Project,
  Revision,
  RevisionDiff,
  ServerKey,
  Session,
  SimulationResult,
  Versioned,
} from './types';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly correlationId: string | undefined;

  constructor(status: number, problem: ProblemDetails = {}) {
    super(problem.detail ?? `Control API request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem.code ?? 'HTTP_ERROR';
    this.correlationId = problem.correlationId;
  }
}

let csrfPromise: Promise<{ readonly headerName: string; readonly token: string }> | null = null;

async function csrf(): Promise<{ readonly headerName: string; readonly token: string }> {
  csrfPromise ??= request('/api/v1/auth/csrf');
  return csrfPromise;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...init.headers },
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}) as ProblemDetails);
    throw new ApiError(response.status, problem as ProblemDetails);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function versioned<T>(path: string): Promise<Versioned<T>> {
  const response = await fetch(path, {
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}) as ProblemDetails);
    throw new ApiError(response.status, problem as ProblemDetails);
  }
  return { value: (await response.json()) as T, etag: response.headers.get('etag') ?? '' };
}

async function mutate<T>(
  path: string,
  method: 'POST' | 'PATCH' | 'PUT',
  body?: unknown,
  etag?: string,
) {
  const token = await csrf();
  const headers: Record<string, string> = {
    [token.headerName]: token.token,
    ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    ...(etag === undefined ? {} : { 'If-Match': etag }),
  };
  try {
    return await request<T>(path, {
      method,
      headers,
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) csrfPromise = null;
    throw error;
  }
}

export const api = {
  session: () => request<Session>('/api/v1/auth/me'),
  projects: (organizationId: string) =>
    request<readonly Project[]>(`/api/v1/organizations/${organizationId}/projects`),
  createProject: (organizationId: string, body: object) =>
    mutate<Project>(`/api/v1/organizations/${organizationId}/projects`, 'POST', body),
  environments: (projectId: string) =>
    request<readonly Environment[]>(`/api/v1/projects/${projectId}/environments`),
  createEnvironment: (projectId: string, body: object) =>
    mutate<Environment>(`/api/v1/projects/${projectId}/environments`, 'POST', body),
  flags: (projectId: string) => request<readonly Flag[]>(`/api/v1/projects/${projectId}/flags`),
  flag: (flagId: string) => versioned<Flag>(`/api/v1/flags/${flagId}`),
  createFlag: (projectId: string, body: object) =>
    mutate<Flag>(`/api/v1/projects/${projectId}/flags`, 'POST', body),
  updateFlag: (flagId: string, etag: string, body: object) =>
    mutate<Flag>(`/api/v1/flags/${flagId}`, 'PATCH', body, etag),
  draft: (flagId: string, environmentId: string) =>
    versioned<Draft>(`/api/v1/flags/${flagId}/environments/${environmentId}`),
  updateDraft: (flagId: string, environmentId: string, etag: string, body: object) =>
    mutate<Draft>(`/api/v1/flags/${flagId}/environments/${environmentId}`, 'PUT', body, etag),
  reseed: (flagId: string, environmentId: string, etag: string, reason: string) =>
    mutate<Draft>(
      `/api/v1/flags/${flagId}/environments/${environmentId}/rollout/reseed`,
      'POST',
      { reason },
      etag,
    ),
  simulate: (
    environmentId: string,
    body: {
      readonly flagKey: string;
      readonly type: FlagType;
      readonly defaultValue: unknown;
      readonly context: { readonly key: string; readonly attributes: Record<string, unknown> };
    },
  ) => mutate<SimulationResult>(`/api/v1/environments/${environmentId}/evaluate`, 'POST', body),
  publish: (environmentId: string, etag: string, reason: string | null) =>
    mutate<Revision>(
      `/api/v1/environments/${environmentId}/publish`,
      'POST',
      reason === null ? {} : { reason },
      etag,
    ),
  revisions: (environmentId: string) =>
    request<readonly Revision[]>(`/api/v1/environments/${environmentId}/revisions`),
  diff: (environmentId: string, from: number, to: number) =>
    request<RevisionDiff>(
      `/api/v1/environments/${environmentId}/revisions/diff?from=${from}&to=${to}`,
    ),
  rollback: (environmentId: string, etag: string, sourceRevision: number, reason: string) =>
    mutate<Revision>(
      `/api/v1/environments/${environmentId}/rollback`,
      'POST',
      {
        sourceRevision,
        reason,
      },
      etag,
    ),
  serverKeys: (environmentId: string) =>
    request<readonly ServerKey[]>(`/api/v1/environments/${environmentId}/sdk-keys`),
  createServerKey: (environmentId: string, name: string) =>
    mutate<IssuedServerKey>(`/api/v1/environments/${environmentId}/sdk-keys`, 'POST', {
      name,
      expiresAt: null,
    }),
  rotateServerKey: (keyId: string) =>
    mutate<IssuedServerKey>(`/api/v1/sdk-keys/${keyId}/rotate`, 'POST', {
      overlapSeconds: 300,
      expiresAt: null,
    }),
  revokeServerKey: (keyId: string) => mutate<void>(`/api/v1/sdk-keys/${keyId}/revoke`, 'POST'),
  browserKeys: (environmentId: string) =>
    request<readonly BrowserKey[]>(`/api/v1/environments/${environmentId}/client-keys`),
  createBrowserKey: (environmentId: string, name: string, origins: readonly string[]) =>
    mutate<BrowserKey>(`/api/v1/environments/${environmentId}/client-keys`, 'POST', {
      name,
      allowedOrigins: origins,
      expiresAt: null,
    }),
  revokeBrowserKey: (keyId: string) => mutate<void>(`/api/v1/client-keys/${keyId}/revoke`, 'POST'),
  audit: (organizationId: string, search: URLSearchParams) =>
    request<readonly AuditEvent[]>(
      `/api/v1/organizations/${organizationId}/audit?${search.toString()}`,
    ),
  analytics: (environmentId: string, search: URLSearchParams) =>
    request<AnalyticsResponse>(
      `/api/v1/environments/${environmentId}/analytics/evaluations?${search.toString()}`,
    ),
  logout: () => mutate<void>('/api/v1/auth/logout', 'POST'),
};
