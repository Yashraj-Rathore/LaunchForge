import { canonicalize, createEvaluationContext, sha256Hex } from '@launchforge/js-core';
import type { JsonValue } from '@launchforge/js-core';
import { describe, expect, it, vi } from 'vitest';
import { LaunchForgeBrowserClient } from './client.js';

const CLIENT_KEY = 'lf_client_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA';

describe('LaunchForgeBrowserClient', () => {
  it('bootstraps and evaluates locally without another network call', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(snapshotResponse(1, true));
    const client = new LaunchForgeBrowserClient({
      baseUrl: 'https://edge.example',
      clientKey: CLIENT_KEY,
      initialContext: createEvaluationContext('canada-pro-user', { country: 'CA' }),
      streaming: false,
      fetcher,
    });

    await client.start();

    expect(client.boolVariation('new-checkout', false)).toBe(true);
    expect(client.getSnapshotRevision()).toBe(1);
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher.mock.calls[0]?.[1]).toMatchObject({ credentials: 'omit' });
    client.close();
  });

  it('rejects malformed and stale snapshots while retaining last-known-good memory', async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(snapshotResponse(2, true))
      .mockResolvedValueOnce(new Response('{"schemaVersion":1}', { status: 200 }))
      .mockResolvedValueOnce(snapshotResponse(1, false));
    const client = clientWith(fetcher);
    let activations = 0;
    client.subscribe(() => {
      activations += 1;
    });

    await client.start();
    await expect(client.refresh()).rejects.toThrow();
    expect(await client.refresh()).toBe(false);

    expect(client.getSnapshotRevision()).toBe(2);
    expect(client.boolVariation('new-checkout', false)).toBe(true);
    expect(activations).toBe(1);
    client.close();
  });

  it('uses a revision-only SSE hint to fetch and atomically activate the newer snapshot', async () => {
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(
          new TextEncoder().encode('id: 2\nevent: revision\ndata: {"revision":2}\n\n'),
        );
        controller.close();
      },
    });
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(snapshotResponse(1, false))
      .mockResolvedValueOnce(
        new Response(stream, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
      )
      .mockResolvedValueOnce(snapshotResponse(2, true));
    const client = clientWith(fetcher, true);

    await client.start();
    await vi.waitFor(() => expect(client.getSnapshotRevision()).toBe(2));

    expect(client.boolVariation('new-checkout', false)).toBe(true);
    expect(fetcher.mock.calls[1]?.[0]).toBe(
      `https://edge.example/sdk/v1/client/${CLIENT_KEY}/stream`,
    );
    client.close();
  });

  it('notifies subscribers on immutable context replacement and cleans them up', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(snapshotResponse(1, true));
    const client = clientWith(fetcher);
    await client.start();
    let updates = 0;
    const unsubscribe = client.subscribe(() => {
      updates += 1;
    });
    client.setContext(createEvaluationContext('us-free-user', { country: 'US' }));
    unsubscribe();
    client.setContext(createEvaluationContext('canada-pro-user', { country: 'CA' }));
    expect(updates).toBe(1);
    client.close();
    client.close();
  });

  it('keeps analytics opt-in, context-free, batched, and isolated from evaluation', async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(snapshotResponse(3, true))
      .mockResolvedValueOnce(new Response('{}', { status: 503 }));
    const client = new LaunchForgeBrowserClient({
      baseUrl: 'https://edge.example',
      clientKey: CLIENT_KEY,
      initialContext: createEvaluationContext('private-subject', {
        email: 'person@example.test',
      }),
      streaming: false,
      fetcher,
      analytics: { enabled: true, batchSize: 1, queueCapacity: 2, flushIntervalMs: 100 },
    });
    await client.start();

    expect(client.boolVariation('new-checkout', false)).toBe(true);
    await client.flushAnalytics();

    expect(fetcher).toHaveBeenCalledTimes(2);
    const request = fetcher.mock.calls[1]?.[1];
    expect(String(request?.body)).not.toContain('private-subject');
    expect(String(request?.body)).not.toContain('person@example.test');
    expect(String(request?.body)).not.toContain('attributes');
    expect(client.getAnalyticsStatistics()).toMatchObject({
      queued: 1,
      sent: 0,
      dropped: 1,
      failedBatches: 1,
    });
    expect(client.boolVariation('new-checkout', false)).toBe(true);
    client.close();
  });

  it('bounds a hanging analytics request and permits the next batch to flush', async () => {
    vi.useFakeTimers();
    try {
      const analyticsSignals: AbortSignal[] = [];
      const fetcher = vi
        .fn<typeof fetch>()
        .mockResolvedValueOnce(snapshotResponse(4, true))
        .mockImplementationOnce((_input, init) => {
          const analyticsSignal = init?.signal;
          if (!analyticsSignal) {
            throw new Error('Expected analytics request cancellation signal');
          }
          analyticsSignals.push(analyticsSignal);
          return new Promise<Response>((_resolve, reject) => {
            analyticsSignal.addEventListener(
              'abort',
              () => reject(new DOMException('Analytics request timed out', 'AbortError')),
              { once: true },
            );
          });
        })
        .mockResolvedValueOnce(new Response('{}', { status: 202 }));
      const client = new LaunchForgeBrowserClient({
        baseUrl: 'https://edge.example',
        clientKey: CLIENT_KEY,
        initialContext: createEvaluationContext('private-subject'),
        streaming: false,
        fetcher,
        analytics: {
          enabled: true,
          batchSize: 1,
          queueCapacity: 2,
          flushIntervalMs: 1_000,
          requestTimeoutMs: 100,
        },
      });
      await client.start();

      expect(client.boolVariation('new-checkout', false)).toBe(true);
      const timedOutFlush = client.flushAnalytics();
      await vi.advanceTimersByTimeAsync(100);
      await timedOutFlush;

      expect(analyticsSignals).toHaveLength(1);
      expect(analyticsSignals[0]?.aborted).toBe(true);
      expect(client.getAnalyticsStatistics()).toMatchObject({
        queued: 1,
        sent: 0,
        dropped: 1,
        failedBatches: 1,
      });

      expect(client.boolVariation('new-checkout', false)).toBe(true);
      await client.flushAnalytics();
      expect(client.getAnalyticsStatistics()).toMatchObject({
        queued: 2,
        sent: 1,
        dropped: 1,
        failedBatches: 1,
      });
      client.close();
    } finally {
      vi.useRealTimers();
    }
  });
});

function clientWith(fetcher: typeof fetch, streaming = false): LaunchForgeBrowserClient {
  return new LaunchForgeBrowserClient({
    baseUrl: 'https://edge.example',
    clientKey: CLIENT_KEY,
    initialContext: createEvaluationContext('canada-pro-user'),
    pollIntervalMs: 60_000,
    streaming,
    fetcher,
    random: () => 0.5,
  });
}

function snapshotResponse(revision: number, enabled: boolean): Response {
  const projection: JsonValue = {
    schemaVersion: 1,
    algorithmVersion: 1,
    projectKey: 'storefront',
    environmentKey: 'development',
    revision,
    generatedAt: '2026-08-12T12:00:00Z',
    flags: {
      'new-checkout': {
        type: 'boolean',
        enabled,
        clientVisible: true,
        variations: [
          { id: 'off', value: false },
          { id: 'on', value: true },
        ],
        offVariation: 'off',
        defaultVariation: 'on',
        rules: [],
      },
    },
  };
  const checksum = sha256Hex(canonicalize(projection));
  const body = canonicalize({ ...(projection as Record<string, JsonValue>), checksum });
  return new Response(body, {
    status: 200,
    headers: {
      ETag: `"rev-${revision}-${checksum}"`,
      'X-LaunchForge-Revision': String(revision),
      'X-LaunchForge-Checksum': checksum,
      'X-LaunchForge-Schema-Version': '1',
    },
  });
}
