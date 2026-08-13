import type { BrowserClient } from '@launchforge/js-browser';
import { createEvaluationContext } from '@launchforge/js-core';
import type { EvaluationContext, EvaluationDetail, JsonValue } from '@launchforge/js-core';
import { act, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useBooleanFlagDetail } from './hooks.js';
import { LaunchForgeProvider } from './provider.js';

afterEach(() => {
  document.body.replaceChildren();
});

describe('LaunchForgeProvider', () => {
  it('rerenders hooks on activation, forwards context, and cleans subscriptions', async () => {
    const client = new FakeClient();
    const context = createEvaluationContext('canada-pro-user', { country: 'CA' });
    const result = render(
      <LaunchForgeProvider client={client} context={context}>
        <Checkout />
      </LaunchForgeProvider>,
    );

    expect(await screen.findByText('Classic · SNAPSHOT_UNAVAILABLE')).toBeTruthy();
    expect(client.start).toHaveBeenCalledTimes(1);
    expect(client.setContext).toHaveBeenCalledWith(context);

    act(() => client.activate(true, 7));
    expect(screen.getByText('Express · DEFAULT_VARIATION')).toBeTruthy();

    result.unmount();
    expect(client.listenerCount()).toBe(0);
  });
});

function Checkout() {
  const checkout = useBooleanFlagDetail('new-checkout', false);
  return (
    <p>
      {checkout.value ? 'Express' : 'Classic'} · {checkout.reason}
    </p>
  );
}

class FakeClient implements BrowserClient {
  readonly start = vi.fn(async () => undefined);
  readonly refresh = vi.fn(async () => false);
  readonly close = vi.fn();
  readonly flushAnalytics = vi.fn(async () => undefined);
  readonly setContext = vi.fn((context: EvaluationContext) => {
    this.context = context;
  });
  private context = createEvaluationContext('initial');
  private enabled = false;
  private revision: number | null = null;
  private version = 0;
  private readonly listeners = new Set<() => void>();

  getContext(): EvaluationContext {
    return this.context;
  }

  getSnapshotRevision(): number | null {
    return this.revision;
  }

  getVersion = (): number => this.version;

  getAnalyticsStatistics() {
    return { queued: 0, sent: 0, dropped: 0, failedBatches: 0 };
  }

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  listenerCount(): number {
    return this.listeners.size;
  }

  activate(enabled: boolean, revision: number): void {
    this.enabled = enabled;
    this.revision = revision;
    this.version += 1;
    this.listeners.forEach((listener) => listener());
  }

  boolVariation(): boolean {
    return this.enabled;
  }

  boolVariationDetail(): EvaluationDetail<boolean> {
    return this.revision === null
      ? { value: false, reason: 'SNAPSHOT_UNAVAILABLE', errorKind: 'SNAPSHOT_UNAVAILABLE' }
      : { value: this.enabled, reason: 'DEFAULT_VARIATION', snapshotRevision: this.revision };
  }

  stringVariation(_flagKey: string, defaultValue: string): string {
    return defaultValue;
  }

  stringVariationDetail(_flagKey: string, defaultValue: string): EvaluationDetail<string> {
    return { value: defaultValue, reason: 'SNAPSHOT_UNAVAILABLE' };
  }

  numberVariation(_flagKey: string, defaultValue: number): number {
    return defaultValue;
  }

  numberVariationDetail(_flagKey: string, defaultValue: number): EvaluationDetail<number> {
    return { value: defaultValue, reason: 'SNAPSHOT_UNAVAILABLE' };
  }

  jsonVariation<T extends JsonValue>(_flagKey: string, defaultValue: T): JsonValue | T {
    return defaultValue;
  }

  jsonVariationDetail<T extends JsonValue>(
    _flagKey: string,
    defaultValue: T,
  ): EvaluationDetail<JsonValue | T> {
    return { value: defaultValue, reason: 'SNAPSHOT_UNAVAILABLE' };
  }
}
