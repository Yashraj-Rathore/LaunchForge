import type { BrowserClientOptions } from '@launchforge/js-browser';
import { createEvaluationContext } from '@launchforge/js-core';
import type { EvaluationContext } from '@launchforge/js-core';
import {
  LaunchForgeProvider,
  useBooleanFlagDetail,
  useLaunchForgeClient,
} from '@launchforge/react-sdk';
import { useMemo, useState } from 'react';

interface DemoUser {
  readonly id: string;
  readonly label: string;
  readonly note: string;
  readonly context: EvaluationContext;
}

const USERS: readonly DemoUser[] = [
  {
    id: 'canada-pro-user',
    label: 'Maya / Canada Pro',
    note: 'Matches the ordered CA + Pro targeting rule.',
    context: createEvaluationContext('canada-pro-user', {
      country: 'CA',
      plan: 'pro',
      userId: 'maya-pro-01',
    }),
  },
  {
    id: 'us-free-user',
    label: 'Alex / US Free',
    note: 'Misses targeting and lands in deterministic rollout bucket 50000.',
    context: createEvaluationContext('us-free-user', {
      country: 'US',
      plan: 'free',
      userId: 'demo-60157',
    }),
  },
  {
    id: 'internal-tester',
    label: 'Ivy / Rollout cohort',
    note: 'Bucket 29240 stays out at 10% and enters when the rollout expands to 50%.',
    context: createEvaluationContext('internal-tester', {
      country: 'US',
      plan: 'internal',
      userId: 'demo-26',
    }),
  },
  {
    id: 'anonymous-user',
    label: 'Guest / Missing key',
    note: 'Has no rollout attribute, so evaluation uses the configured safe default.',
    context: createEvaluationContext('anonymous-user', {
      country: 'US',
      plan: 'free',
    }),
  },
];

export function App() {
  const [userId, setUserId] = useState(USERS[0]?.id ?? '');
  const selected = USERS.find((user) => user.id === userId) ?? USERS[0];
  if (selected === undefined) {
    throw new Error('Demo users are missing');
  }
  const options = useMemo<BrowserClientOptions>(
    () => ({
      baseUrl: import.meta.env.VITE_LAUNCHFORGE_EDGE_URL ?? window.location.origin,
      clientKey: requireClientKey(import.meta.env.VITE_LAUNCHFORGE_CLIENT_KEY),
      initialContext: selected.context,
      pollIntervalMs: 30_000,
      streaming: true,
    }),
    [],
  );

  return (
    <LaunchForgeProvider options={options} context={selected.context}>
      <Storefront selected={selected} onSelect={setUserId} />
    </LaunchForgeProvider>
  );
}

function Storefront({
  selected,
  onSelect,
}: {
  readonly selected: DemoUser;
  readonly onSelect: (id: string) => void;
}) {
  const client = useLaunchForgeClient();
  const checkout = useBooleanFlagDetail('new-checkout', false);

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="Northstar Commerce home">
          <span className="brand-mark">N</span>
          <span>Northstar Commerce</span>
        </a>
        <div className="runtime-status" aria-label="Live configuration status">
          <span className="live-dot" aria-hidden="true" />
          <span className="environment">
            Development / revision {client.getSnapshotRevision() ?? '—'}
          </span>
        </div>
      </header>

      <main id="top">
        <section className="hero">
          <div className="eyebrow">FIELD NOTES / AUTUMN 2026</div>
          <p className="kicker">Equipment for the long way home</p>
          <h1>
            Built for the
            <br />
            edge of ordinary.
          </h1>
          <p className="intro">
            Fictional trail gear, tested by a local flag evaluator. Change the visitor to see stable
            targeting and rollout behavior.
          </p>
          <a className="shop-link" href="#checkout">
            Explore the collection <span>→</span>
          </a>
        </section>

        <section className="field-grid" aria-label="Featured fictional products">
          <article className="product product-tent">
            <span className="product-number">01</span>
            <div>
              <p>Ridgeline Series</p>
              <h2>Aster Tent</h2>
              <span>$319</span>
            </div>
          </article>
          <article className="product product-pack">
            <span className="product-number">02</span>
            <div>
              <p>Traverse Series</p>
              <h2>Moraine 42L</h2>
              <span>$188</span>
            </div>
          </article>
        </section>

        <section className="control-deck" id="checkout">
          <div className="visitor-panel">
            <div className="section-label">DETERMINISTIC VISITOR</div>
            <div className="visitor-tabs" role="group" aria-label="Demo visitor">
              {USERS.map((user) => (
                <button
                  key={user.id}
                  className={selected.id === user.id ? 'active' : ''}
                  onClick={() => onSelect(user.id)}
                  type="button"
                >
                  {user.label}
                </button>
              ))}
            </div>
            <p>{selected.note}</p>
          </div>

          <div
            aria-live="polite"
            className={`checkout-card ${checkout.value ? 'express' : 'classic'}`}
          >
            <div>
              <div className="section-label">LIVE FLAG / NEW-CHECKOUT</div>
              <h2 data-testid="checkout-experience">
                {checkout.value ? 'Express checkout' : 'Classic checkout'}
              </h2>
              <p>
                {checkout.value
                  ? 'One calm step from cart to trail.'
                  : 'The dependable multi-step checkout remains active.'}
              </p>
            </div>
            <dl className="diagnostics">
              <div>
                <dt>Reason</dt>
                <dd data-testid="reason">{checkout.reason}</dd>
              </div>
              <div>
                <dt>Revision</dt>
                <dd data-testid="revision">{checkout.snapshotRevision ?? '—'}</dd>
              </div>
              <div>
                <dt>Bucket</dt>
                <dd data-testid="bucket">{checkout.rolloutBucket ?? '—'}</dd>
              </div>
            </dl>
          </div>
        </section>

        <section className="demo-route" aria-labelledby="demo-route-title">
          <div>
            <div className="section-label">DELIBERATE DEMO ROUTE</div>
            <h2 id="demo-route-title">One change at a time.</h2>
            <p>
              Pause after each step so the selected visitor, evaluation reason, bucket, and revision
              remain visible before moving on.
            </p>
          </div>
          <ol>
            <li>
              <span>01</span>
              Compare targeted and non-targeted visitors.
            </li>
            <li>
              <span>02</span>
              Expand the stable rollout from 10% to 50%.
            </li>
            <li>
              <span>03</span>
              Publish and watch the revision advance without redeploying.
            </li>
            <li>
              <span>04</span>
              Use the kill switch and confirm the safe fallback.
            </li>
          </ol>
        </section>
      </main>

      <footer>
        Client configuration is public and inspectable. This demo never treats a flag as
        authorization.
      </footer>
    </div>
  );
}

function requireClientKey(value: string | undefined): string {
  if (value === undefined || value.length === 0) {
    throw new Error('VITE_LAUNCHFORGE_CLIENT_KEY is required');
  }
  return value;
}
