import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { api, ApiError } from '../api';
import { Icon } from '../Icon';
import type { WorkspaceContext } from '../workspace';
import { PageError, PageHeading, PageState } from './FlagsPage';

export function AnalyticsPage() {
  const workspace = useOutletContext<WorkspaceContext>();
  const [from, setFrom] = useState(() => localInput(new Date(Date.now() - 24 * 60 * 60 * 1000)));
  const [to, setTo] = useState(() => localInput(new Date()));
  const [flagKey, setFlagKey] = useState('');
  const [variationId, setVariationId] = useState('');
  const [bucket, setBucket] = useState<'HOUR' | 'DAY'>('HOUR');
  const search = useMemo(() => {
    const value = new URLSearchParams({
      from: new Date(from).toISOString(),
      to: new Date(to).toISOString(),
      bucket,
      limit: '500',
    });
    if (flagKey.trim() !== '') value.set('flagKey', flagKey.trim());
    if (variationId.trim() !== '') value.set('variationId', variationId.trim());
    return value;
  }, [bucket, flagKey, from, to, variationId]);
  const analytics = useQuery({
    queryKey: ['analytics', workspace.environment.id, search.toString()],
    queryFn: () => api.analytics(workspace.environment.id, search),
  });
  const unavailable =
    analytics.error instanceof ApiError && analytics.error.code === 'ANALYTICS_UNAVAILABLE';

  return (
    <>
      <PageHeading
        eyebrow="Optional telemetry"
        title="Evaluation analytics"
        detail="Operational counts from explicitly opted-in SDKs. This view does not measure experiment significance and makes no causal claim."
      />
      <div className="notice" role="note">
        <strong>Privacy boundary</strong>
        <span>
          Events contain bounded IDs, timestamps, revision, reason, flag, and variation only—never
          raw targeting context or subject identifiers.
        </span>
      </div>
      <section className="panel analytics-filters" aria-label="Analytics filters">
        <div className="filter-intro">
          <span className="metric-icon violet">
            <Icon name="analytics" />
          </span>
          <div>
            <strong>Explore evaluation volume</strong>
            <span>Filters update the bounded operational query automatically.</span>
          </div>
        </div>
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
        <label>
          Flag key
          <input
            placeholder="All flags"
            value={flagKey}
            onChange={(event) => setFlagKey(event.target.value)}
          />
        </label>
        <label>
          Variation
          <input
            placeholder="All variations"
            value={variationId}
            onChange={(event) => setVariationId(event.target.value)}
          />
        </label>
        <label>
          Time bucket
          <select
            value={bucket}
            onChange={(event) => setBucket(event.target.value as 'HOUR' | 'DAY')}
          >
            <option value="HOUR">Hourly</option>
            <option value="DAY">Daily</option>
          </select>
        </label>
      </section>
      {analytics.isPending ? (
        <PageState title="Loading analytics" detail="Reading bounded aggregate counts…" />
      ) : unavailable ? (
        <PageState
          title="Analytics unavailable"
          detail="Optional analytics is disabled or its isolated store is unavailable. Flag evaluation, configuration delivery, publish, and rollback are unaffected."
        />
      ) : analytics.isError ? (
        <PageError
          title="Analytics query failed"
          error={analytics.error}
          retry={analytics.refetch}
        />
      ) : analytics.data.rows.length === 0 ? (
        <PageState
          title="No opted-in evaluations"
          detail="No matching SDK analytics events were retained for this range."
        />
      ) : (
        <section className="panel analytics-results" aria-label="Operational evaluation counts">
          <div className="analytics-summary">
            <div>
              <span>Total evaluations</span>
              <strong>
                {analytics.data.rows
                  .reduce((total, row) => total + row.evaluations, 0)
                  .toLocaleString()}
              </strong>
            </div>
            <div>
              <span>Matching series</span>
              <strong>{analytics.data.rows.length.toLocaleString()}</strong>
            </div>
            <div>
              <span>Time granularity</span>
              <strong>{bucket === 'HOUR' ? 'Hourly' : 'Daily'}</strong>
            </div>
          </div>
          <p className="help">{analytics.data.interpretation}</p>
          <div className="analytics-table-wrap">
            <table className="analytics-table">
              <thead>
                <tr>
                  <th>Bucket</th>
                  <th>Flag</th>
                  <th>Variation</th>
                  <th>Evaluations</th>
                </tr>
              </thead>
              <tbody>
                {analytics.data.rows.map((row) => (
                  <tr key={`${row.bucketStart}:${row.flagKey}:${row.variationId ?? ''}`}>
                    <td data-label="Bucket">{new Date(row.bucketStart).toLocaleString()}</td>
                    <td data-label="Flag">
                      <code>{row.flagKey}</code>
                    </td>
                    <td data-label="Variation">
                      <code>{row.variationId ?? 'none'}</code>
                    </td>
                    <td data-label="Evaluations">{row.evaluations.toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </>
  );
}

function localInput(value: Date): string {
  const offset = value.getTimezoneOffset() * 60_000;
  return new Date(value.getTime() - offset).toISOString().slice(0, 16);
}
