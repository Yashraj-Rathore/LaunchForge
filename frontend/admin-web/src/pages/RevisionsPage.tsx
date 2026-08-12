import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { api } from '../api';
import { canPublish, MutationError, queryClient } from '../App';
import type { Revision } from '../types';
import type { WorkspaceContext } from '../workspace';
import { PageError, PageHeading, PageState } from './FlagsPage';

export function RevisionsPage() {
  const workspace = useOutletContext<WorkspaceContext>();
  const revisions = useQuery({
    queryKey: ['revisions', workspace.environment.id],
    queryFn: () => api.revisions(workspace.environment.id),
  });
  const [selected, setSelected] = useState<Revision | null>(null);
  if (revisions.isPending)
    return <PageState title="Loading revisions" detail="Reading immutable environment history…" />;
  if (revisions.isError)
    return (
      <PageError
        title="Revision history unavailable"
        error={revisions.error}
        retry={revisions.refetch}
      />
    );
  return (
    <>
      <PageHeading
        eyebrow="Immutable history"
        title="Revisions"
        detail="Compare durable configuration changes or restore historical content by creating a newer revision."
      />
      {revisions.data.length === 0 ? (
        <PageState
          title="Nothing published yet"
          detail="The first successful publish will create revision 1."
        />
      ) : (
        <div className="revision-list">
          {revisions.data.map((revision, index) => (
            <RevisionRow
              key={revision.revision}
              revision={revision}
              previous={revisions.data[index + 1]}
              onRollback={setSelected}
              environmentId={workspace.environment.id}
              canRollback={canPublish(workspace.organization.role, workspace.environment)}
            />
          ))}
        </div>
      )}
      {selected && (
        <RollbackDialog
          workspace={workspace}
          revision={selected}
          onClose={() => setSelected(null)}
        />
      )}
    </>
  );
}

function RevisionRow({
  revision,
  previous,
  environmentId,
  canRollback,
  onRollback,
}: {
  readonly revision: Revision;
  readonly previous: Revision | undefined;
  readonly environmentId: string;
  readonly canRollback: boolean;
  readonly onRollback: (revision: Revision) => void;
}) {
  const [compare, setCompare] = useState(false);
  const diff = useQuery({
    queryKey: ['revision-diff', environmentId, previous?.revision, revision.revision],
    queryFn: () =>
      api.diff(environmentId, previous?.revision ?? revision.revision, revision.revision),
    enabled: compare && previous !== undefined,
  });
  return (
    <article className="revision-card">
      <div className="revision-number">
        <span>REV</span>
        <strong>{revision.revision}</strong>
      </div>
      <div className="revision-body">
        <div className="revision-title">
          <div>
            <h2>{revision.reason || 'Published configuration'}</h2>
            <p>
              {new Date(revision.createdAt).toLocaleString()} · {revision.actorSubject}
            </p>
          </div>
          {revision.sourceRevision !== null && (
            <span className="rollback-chip">Restored from {revision.sourceRevision}</span>
          )}
        </div>
        <code className="checksum">{revision.checksum}</code>
        <div className="button-row">
          <button
            className="button ghost"
            disabled={previous === undefined}
            onClick={() => setCompare((value) => !value)}
            type="button"
          >
            {compare ? 'Hide diff' : 'Compare to previous'}
          </button>
          <button
            className="button secondary"
            disabled={!canRollback}
            onClick={() => onRollback(revision)}
            type="button"
          >
            Restore this content
          </button>
        </div>
        {compare &&
          previous !== undefined &&
          (diff.isPending ? (
            <p role="status">Calculating structured diff…</p>
          ) : diff.isError ? (
            <p className="inline-error" role="alert">
              {diff.error.message}
            </p>
          ) : (
            <div className="diff-grid">
              <DiffList title="Added" values={diff.data.addedFlagKeys} />
              <DiffList title="Changed" values={diff.data.changedFlagKeys} />
              <DiffList title="Removed" values={diff.data.removedFlagKeys} />
            </div>
          ))}
      </div>
    </article>
  );
}

function DiffList({
  title,
  values,
}: {
  readonly title: string;
  readonly values: readonly string[];
}) {
  return (
    <div>
      <span>{title}</span>
      {values.length === 0 ? (
        <em>None</em>
      ) : (
        <ul>
          {values.map((value) => (
            <li key={value}>
              <code>{value}</code>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function RollbackDialog({
  workspace,
  revision,
  onClose,
}: {
  readonly workspace: WorkspaceContext;
  readonly revision: Revision;
  readonly onClose: () => void;
}) {
  const [reason, setReason] = useState(`Restore known-good revision ${revision.revision}`);
  const [created, setCreated] = useState<number | null>(null);
  const rollback = useMutation({
    mutationFn: () =>
      api.rollback(
        workspace.environment.id,
        String(workspace.environment.version),
        revision.revision,
        reason,
      ),
    onSuccess: async (result) => {
      setCreated(result.revision);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['revisions', workspace.environment.id] }),
        queryClient.invalidateQueries({ queryKey: ['environments', workspace.project.id] }),
      ]);
    },
    onError: async () => {
      await queryClient.invalidateQueries({ queryKey: ['environments', workspace.project.id] });
    },
  });
  return (
    <div className="modal-backdrop">
      <section aria-labelledby="rollback-title" aria-modal="true" className="modal" role="dialog">
        <div className="panel-heading">
          <div>
            <span className="section-kicker">Safe restore</span>
            <h2 id="rollback-title">Restore revision {revision.revision}</h2>
          </div>
          <button aria-label="Close rollback dialog" onClick={onClose} type="button">
            ×
          </button>
        </div>
        <div className="notice warning">
          <strong>History never rewinds</strong>
          <span>
            This publishes the selected content as a new revision greater than{' '}
            {workspace.environment.currentRevision}.
          </span>
        </div>
        <label>
          Required rollback reason
          <textarea value={reason} onChange={(event) => setReason(event.target.value)} />
        </label>
        <MutationError error={rollback.error} />
        {rollback.error && (
          <p className="help">
            Current server revision was reconciled after the failed request. Review history before
            retrying.
          </p>
        )}
        {created !== null && (
          <p className="publish-success" aria-live="assertive">
            Revision {created} now restores content from revision {revision.revision}.
          </p>
        )}
        <div className="button-row">
          <button className="button ghost" onClick={onClose} type="button">
            Cancel
          </button>
          <button
            className="button danger"
            disabled={reason.trim().length === 0 || rollback.isPending}
            onClick={() => rollback.mutate()}
            type="button"
          >
            Publish rollback revision
          </button>
        </div>
      </section>
    </div>
  );
}
