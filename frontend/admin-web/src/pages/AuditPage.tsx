import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { api } from '../api';
import type { AuditEvent } from '../types';
import type { WorkspaceContext } from '../workspace';
import { PageError, PageHeading, PageState } from './FlagsPage';

export function AuditPage() {
  const workspace = useOutletContext<WorkspaceContext>();
  const [actor, setActor] = useState('');
  const [action, setAction] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [environmentOnly, setEnvironmentOnly] = useState(false);
  const search = useMemo(() => {
    const value = new URLSearchParams({ projectId: workspace.project.id, limit: '100' });
    if (environmentOnly) value.set('environmentId', workspace.environment.id);
    if (actor.trim() !== '') value.set('actor', actor.trim());
    if (action.trim() !== '') value.set('action', action.trim());
    if (from !== '') value.set('from', new Date(from).toISOString());
    if (to !== '') value.set('to', new Date(to).toISOString());
    return value;
  }, [action, actor, environmentOnly, from, to, workspace.environment.id, workspace.project.id]);
  const events = useQuery({
    queryKey: ['audit', workspace.organization.id, search.toString()],
    queryFn: () => api.audit(workspace.organization.id, search),
  });

  return (
    <>
      <PageHeading
        eyebrow="Accountability"
        title="Audit trail"
        detail="Browse safe mutation metadata. Credentials, cookies, authorization headers, and evaluation context are never rendered here."
      />
      <section className="panel audit-filters" aria-label="Audit filters">
        <label>
          Actor subject
          <input
            placeholder="Exact subject"
            value={actor}
            onChange={(event) => setActor(event.target.value)}
          />
        </label>
        <label>
          Action
          <input
            placeholder="For example FLAG_UPDATED"
            value={action}
            onChange={(event) => setAction(event.target.value)}
          />
        </label>
        <label>
          From
          <input
            type="datetime-local"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
        </label>
        <label>
          To
          <input type="datetime-local" value={to} onChange={(event) => setTo(event.target.value)} />
        </label>
        <label className="check-row">
          <input
            checked={environmentOnly}
            onChange={(event) => setEnvironmentOnly(event.target.checked)}
            type="checkbox"
          />
          Only {workspace.environment.name}
        </label>
      </section>
      {events.isPending ? (
        <PageState title="Loading audit events" detail="Reading tenant-scoped activity…" />
      ) : events.isError ? (
        <PageError title="Audit trail unavailable" error={events.error} retry={events.refetch} />
      ) : events.data.length === 0 ? (
        <PageState
          title="No matching activity"
          detail="Adjust the filters or make a managed change."
        />
      ) : (
        <section className="audit-list" aria-label="Audit events">
          {events.data.map((event) => (
            <AuditRow event={event} key={event.id} />
          ))}
        </section>
      )}
    </>
  );
}

function AuditRow({ event }: { readonly event: AuditEvent }) {
  return (
    <article className="audit-row">
      <div className="audit-time">
        <strong>{new Date(event.createdAt).toLocaleDateString()}</strong>
        <span>{new Date(event.createdAt).toLocaleTimeString()}</span>
      </div>
      <div className="audit-body">
        <div className="audit-title">
          <code>{event.action}</code>
          <span>{event.targetType}</span>
        </div>
        <p>{event.summary ?? 'Managed resource changed.'}</p>
        {event.reason !== null && <blockquote>{event.reason}</blockquote>}
        <dl className="audit-metadata">
          <div>
            <dt>Actor</dt>
            <dd>{event.actor}</dd>
          </div>
          <div>
            <dt>Target</dt>
            <dd>{event.targetId}</dd>
          </div>
          <div>
            <dt>Revision</dt>
            <dd>
              {event.fromRevision ?? '—'} → {event.toRevision ?? '—'}
            </dd>
          </div>
          <div>
            <dt>Correlation</dt>
            <dd>{event.correlationId}</dd>
          </div>
        </dl>
      </div>
    </article>
  );
}
