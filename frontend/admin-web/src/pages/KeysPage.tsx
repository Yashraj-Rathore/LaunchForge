import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { api } from '../api';
import { MutationError, queryClient } from '../App';
import type { BrowserKey, IssuedServerKey, ServerKey } from '../types';
import type { WorkspaceContext } from '../workspace';
import { PageError, PageHeading, PageState } from './FlagsPage';

export function KeysPage() {
  const workspace = useOutletContext<WorkspaceContext>();
  const [tab, setTab] = useState<'server' | 'browser'>('server');
  const permitted =
    workspace.organization.role === 'OWNER' ||
    workspace.organization.role === 'ADMIN' ||
    (workspace.organization.role === 'DEVELOPER' && workspace.environment.kind !== 'PRODUCTION');
  return (
    <>
      <PageHeading
        eyebrow="Credential boundary"
        title="SDK keys"
        detail="Server secrets and public browser identifiers are separate credential classes scoped to this environment."
      />
      {!permitted && (
        <div className="notice denied" role="status">
          <strong>Key management denied</strong>
          <span>
            Your role can inspect safe metadata but cannot create, rotate, or revoke keys in this
            environment.
          </span>
        </div>
      )}
      <div className="tabs" role="tablist" aria-label="SDK key type">
        <button
          aria-selected={tab === 'server'}
          onClick={() => setTab('server')}
          role="tab"
          type="button"
        >
          Server SDK keys
        </button>
        <button
          aria-selected={tab === 'browser'}
          onClick={() => setTab('browser')}
          role="tab"
          type="button"
        >
          Browser client keys
        </button>
      </div>
      {tab === 'server' ? (
        <ServerKeys environmentId={workspace.environment.id} permitted={permitted} />
      ) : (
        <BrowserKeys environmentId={workspace.environment.id} permitted={permitted} />
      )}
    </>
  );
}

function ServerKeys({
  environmentId,
  permitted,
}: {
  readonly environmentId: string;
  readonly permitted: boolean;
}) {
  const keys = useQuery({
    queryKey: ['server-keys', environmentId],
    queryFn: () => api.serverKeys(environmentId),
  });
  const [name, setName] = useState('Storefront server');
  const [issued, setIssued] = useState<IssuedServerKey | null>(null);
  const create = useMutation({
    mutationFn: () => api.createServerKey(environmentId, name),
    onSuccess: async (result) => {
      setIssued(result);
      create.reset();
      await queryClient.invalidateQueries({ queryKey: ['server-keys', environmentId] });
    },
  });
  const rotate = useMutation({
    mutationFn: (keyId: string) => api.rotateServerKey(keyId),
    onSuccess: async (result) => {
      setIssued(result);
      rotate.reset();
      await queryClient.invalidateQueries({ queryKey: ['server-keys', environmentId] });
    },
  });
  const revoke = useMutation({
    mutationFn: (keyId: string) => api.revokeServerKey(keyId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['server-keys', environmentId] });
    },
  });
  if (keys.isPending)
    return (
      <PageState
        title="Loading server keys"
        detail="Only safe key metadata is returned by list operations…"
      />
    );
  if (keys.isError)
    return <PageError title="Server keys unavailable" error={keys.error} retry={keys.refetch} />;
  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <span className="section-kicker">Trusted server environments only</span>
          <h2>Server keys</h2>
        </div>
        {permitted && (
          <div className="inline-create">
            <label>
              Key name
              <input value={name} onChange={(event) => setName(event.target.value)} />
            </label>
            <button
              className="button primary"
              disabled={create.isPending}
              onClick={() => create.mutate()}
              type="button"
            >
              Create key
            </button>
          </div>
        )}
      </div>
      <MutationError error={create.error ?? rotate.error ?? revoke.error} />
      {keys.data.length === 0 ? (
        <div className="empty-inline">No server SDK keys exist for this environment.</div>
      ) : (
        <div className="key-list">
          {keys.data.map((key) => (
            <ServerKeyRow
              key={key.id}
              value={key}
              permitted={permitted}
              onRotate={() => rotate.mutate(key.id)}
              onRevoke={() => revoke.mutate(key.id)}
            />
          ))}
        </div>
      )}
      {issued && <SecretDialog issued={issued} onClose={() => setIssued(null)} />}
    </section>
  );
}

function ServerKeyRow({
  value,
  permitted,
  onRotate,
  onRevoke,
}: {
  readonly value: ServerKey;
  readonly permitted: boolean;
  readonly onRotate: () => void;
  readonly onRevoke: () => void;
}) {
  return (
    <article className="key-row">
      <div>
        <div className="key-title">
          <h3>{value.name}</h3>
          <span className={`status-chip ${value.status.toLowerCase()}`}>{value.status}</span>
        </div>
        <code>{value.fingerprint}</code>
        <p>
          Created {new Date(value.createdAt).toLocaleDateString()} · Last used{' '}
          {value.lastUsedAt ? new Date(value.lastUsedAt).toLocaleString() : 'never'}
        </p>
      </div>
      {permitted && value.status === 'ACTIVE' && (
        <div className="button-row">
          <button className="button ghost" onClick={onRotate} type="button">
            Rotate
          </button>
          <button className="button danger" onClick={onRevoke} type="button">
            Revoke
          </button>
        </div>
      )}
    </article>
  );
}

function SecretDialog({
  issued,
  onClose,
}: {
  readonly issued: IssuedServerKey;
  readonly onClose: () => void;
}) {
  const [copied, setCopied] = useState(false);
  return (
    <div className="modal-backdrop">
      <section
        aria-labelledby="secret-title"
        aria-modal="true"
        className="modal secret-modal"
        role="dialog"
      >
        <span className="section-kicker">One-time secret</span>
        <h2 id="secret-title">Copy {issued.key.name} now</h2>
        <p>
          LaunchForge will never return this server credential again. Store it in your deployment
          secret manager.
        </p>
        <code data-testid="one-time-secret">{issued.secret}</code>
        <div className="button-row">
          <button
            className="button secondary"
            onClick={() =>
              void navigator.clipboard.writeText(issued.secret).then(() => setCopied(true))
            }
            type="button"
          >
            {copied ? 'Copied' : 'Copy secret'}
          </button>
          <button className="button primary" onClick={onClose} type="button">
            I stored it securely
          </button>
        </div>
      </section>
    </div>
  );
}

function BrowserKeys({
  environmentId,
  permitted,
}: {
  readonly environmentId: string;
  readonly permitted: boolean;
}) {
  const keys = useQuery({
    queryKey: ['browser-keys', environmentId],
    queryFn: () => api.browserKeys(environmentId),
  });
  const [name, setName] = useState('Storefront browser');
  const [origins, setOrigins] = useState('http://localhost:5174');
  const create = useMutation({
    mutationFn: () =>
      api.createBrowserKey(
        environmentId,
        name,
        origins
          .split(/\r?\n/u)
          .map((value) => value.trim())
          .filter(Boolean),
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['browser-keys', environmentId] });
    },
  });
  const revoke = useMutation({
    mutationFn: (keyId: string) => api.revokeBrowserKey(keyId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['browser-keys', environmentId] });
    },
  });
  if (keys.isPending)
    return (
      <PageState
        title="Loading browser keys"
        detail="Reading public client identifiers and exact-origin policies…"
      />
    );
  if (keys.isError)
    return <PageError title="Browser keys unavailable" error={keys.error} retry={keys.refetch} />;
  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <span className="section-kicker">Public, inspectable identifiers</span>
          <h2>Browser client keys</h2>
        </div>
      </div>
      <p className="help">
        Browser keys retrieve only client-visible configuration. Origin rules are abuse controls—not
        confidentiality or authorization.
      </p>
      {permitted && (
        <div className="browser-key-create">
          <label>
            Key name
            <input value={name} onChange={(event) => setName(event.target.value)} />
          </label>
          <label>
            Exact allowed origins, one per line
            <textarea value={origins} onChange={(event) => setOrigins(event.target.value)} />
          </label>
          <button
            className="button primary"
            disabled={create.isPending}
            onClick={() => create.mutate()}
            type="button"
          >
            Create browser key
          </button>
        </div>
      )}
      <MutationError error={create.error ?? revoke.error} />
      {keys.data.length === 0 ? (
        <div className="empty-inline">No public browser keys exist for this environment.</div>
      ) : (
        <div className="key-list">
          {keys.data.map((key) => (
            <BrowserKeyRow
              key={key.id}
              value={key}
              permitted={permitted}
              onRevoke={() => revoke.mutate(key.id)}
            />
          ))}
        </div>
      )}
    </section>
  );
}

function BrowserKeyRow({
  value,
  permitted,
  onRevoke,
}: {
  readonly value: BrowserKey;
  readonly permitted: boolean;
  readonly onRevoke: () => void;
}) {
  return (
    <article className="key-row">
      <div>
        <div className="key-title">
          <h3>{value.name}</h3>
          <span className={`status-chip ${value.status.toLowerCase()}`}>{value.status}</span>
        </div>
        <code>{value.clientKey}</code>
        <p>{value.allowedOrigins.join(' · ')}</p>
      </div>
      {permitted && value.status === 'ACTIVE' && (
        <button className="button danger" onClick={onRevoke} type="button">
          Revoke
        </button>
      )}
    </article>
  );
}
