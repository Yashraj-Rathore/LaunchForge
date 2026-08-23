import {
  createEvaluationContext,
  evaluateBoolean,
  evaluateJson,
  evaluateNumber,
  evaluateString,
  parseSnapshot,
} from '@launchforge/js-core';
import type {
  CompiledSnapshot,
  EvaluationContext,
  EvaluationDetail,
  JsonValue,
} from '@launchforge/js-core';

const CLIENT_KEY = /^lf_client_[A-Za-z0-9_-]{32}$/u;
const DEFAULT_POLL_INTERVAL_MS = 30_000;
const DEFAULT_BOOTSTRAP_TIMEOUT_MS = 5_000;
const DEFAULT_MAXIMUM_SNAPSHOT_BYTES = 1024 * 1024;
const DEFAULT_ANALYTICS_FLUSH_INTERVAL_MS = 1_000;
const DEFAULT_ANALYTICS_QUEUE_CAPACITY = 1_000;
const DEFAULT_ANALYTICS_BATCH_SIZE = 50;
const DEFAULT_ANALYTICS_REQUEST_TIMEOUT_MS = 2_000;

export interface BrowserAnalyticsOptions {
  /** Analytics is disabled unless this literal opt-in is present. */
  readonly enabled: true;
  readonly flushIntervalMs?: number;
  readonly queueCapacity?: number;
  readonly batchSize?: number;
  readonly requestTimeoutMs?: number;
}

export interface BrowserAnalyticsStatistics {
  readonly queued: number;
  readonly sent: number;
  readonly dropped: number;
  readonly failedBatches: number;
}

export interface BrowserClientOptions {
  readonly baseUrl: string;
  readonly clientKey: string;
  readonly initialContext: EvaluationContext;
  readonly pollIntervalMs?: number;
  readonly bootstrapTimeoutMs?: number;
  readonly maximumSnapshotBytes?: number;
  readonly streaming?: boolean;
  readonly fetcher?: typeof fetch;
  readonly random?: () => number;
  readonly analytics?: BrowserAnalyticsOptions;
}

export interface BrowserClient {
  start(): Promise<void>;
  refresh(): Promise<boolean>;
  close(): void;
  setContext(context: EvaluationContext): void;
  getContext(): EvaluationContext;
  getSnapshotRevision(): number | null;
  getVersion(): number;
  subscribe(listener: () => void): () => void;
  flushAnalytics(): Promise<void>;
  getAnalyticsStatistics(): BrowserAnalyticsStatistics;
  boolVariation(flagKey: string, defaultValue: boolean): boolean;
  boolVariationDetail(flagKey: string, defaultValue: boolean): EvaluationDetail<boolean>;
  stringVariation(flagKey: string, defaultValue: string): string;
  stringVariationDetail(flagKey: string, defaultValue: string): EvaluationDetail<string>;
  numberVariation(flagKey: string, defaultValue: number): number;
  numberVariationDetail(flagKey: string, defaultValue: number): EvaluationDetail<number>;
  jsonVariation<T extends JsonValue>(flagKey: string, defaultValue: T): JsonValue | T;
  jsonVariationDetail<T extends JsonValue>(
    flagKey: string,
    defaultValue: T,
  ): EvaluationDetail<JsonValue | T>;
}

export class LaunchForgeBrowserClient implements BrowserClient {
  private readonly baseUrl: string;
  private readonly clientKey: string;
  private readonly pollIntervalMs: number;
  private readonly bootstrapTimeoutMs: number;
  private readonly maximumSnapshotBytes: number;
  private readonly streaming: boolean;
  private readonly fetcher: typeof fetch;
  private readonly random: () => number;
  private readonly listeners = new Set<() => void>();
  private context: EvaluationContext;
  private snapshot: CompiledSnapshot | null = null;
  private etag: string | null = null;
  private version = 0;
  private closed = false;
  private started = false;
  private refreshPromise: Promise<boolean> | null = null;
  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectResolver: (() => void) | null = null;
  private streamAbort: AbortController | null = null;
  private readonly analyticsEnabled: boolean;
  private readonly analyticsFlushIntervalMs: number;
  private readonly analyticsQueueCapacity: number;
  private readonly analyticsBatchSize: number;
  private readonly analyticsRequestTimeoutMs: number;
  private readonly analyticsQueue: AnalyticsEvent[] = [];
  private analyticsTimer: ReturnType<typeof setTimeout> | null = null;
  private analyticsFlush: Promise<void> | null = null;
  private analyticsAbort: AbortController | null = null;
  private analyticsQueued = 0;
  private analyticsSent = 0;
  private analyticsDropped = 0;
  private analyticsFailedBatches = 0;

  constructor(options: BrowserClientOptions) {
    this.baseUrl = requireBaseUrl(options.baseUrl);
    if (!CLIENT_KEY.test(options.clientKey)) {
      throw new Error('clientKey is not a LaunchForge browser client key');
    }
    this.clientKey = options.clientKey;
    this.context = options.initialContext;
    this.pollIntervalMs = boundedInteger(
      options.pollIntervalMs ?? DEFAULT_POLL_INTERVAL_MS,
      100,
      24 * 60 * 60 * 1000,
      'pollIntervalMs',
    );
    this.bootstrapTimeoutMs = boundedInteger(
      options.bootstrapTimeoutMs ?? DEFAULT_BOOTSTRAP_TIMEOUT_MS,
      100,
      60_000,
      'bootstrapTimeoutMs',
    );
    this.maximumSnapshotBytes = boundedInteger(
      options.maximumSnapshotBytes ?? DEFAULT_MAXIMUM_SNAPSHOT_BYTES,
      1024,
      5 * 1024 * 1024,
      'maximumSnapshotBytes',
    );
    this.streaming = options.streaming ?? true;
    this.fetcher = options.fetcher ?? globalThis.fetch.bind(globalThis);
    this.random = options.random ?? Math.random;
    this.analyticsEnabled = options.analytics?.enabled === true;
    this.analyticsFlushIntervalMs = boundedInteger(
      options.analytics?.flushIntervalMs ?? DEFAULT_ANALYTICS_FLUSH_INTERVAL_MS,
      100,
      5 * 60 * 1000,
      'analytics.flushIntervalMs',
    );
    this.analyticsQueueCapacity = boundedInteger(
      options.analytics?.queueCapacity ?? DEFAULT_ANALYTICS_QUEUE_CAPACITY,
      1,
      100_000,
      'analytics.queueCapacity',
    );
    this.analyticsBatchSize = boundedInteger(
      options.analytics?.batchSize ?? DEFAULT_ANALYTICS_BATCH_SIZE,
      1,
      100,
      'analytics.batchSize',
    );
    this.analyticsRequestTimeoutMs = boundedInteger(
      options.analytics?.requestTimeoutMs ?? DEFAULT_ANALYTICS_REQUEST_TIMEOUT_MS,
      100,
      30_000,
      'analytics.requestTimeoutMs',
    );
    if (this.analyticsBatchSize > this.analyticsQueueCapacity) {
      throw new Error('analytics.batchSize must fit analytics.queueCapacity');
    }
  }

  async start(): Promise<void> {
    if (this.started) {
      return;
    }
    if (this.closed) {
      throw new Error('LaunchForge client is closed');
    }
    this.started = true;
    try {
      await this.refresh();
    } finally {
      if (!this.closed) {
        this.schedulePoll();
        if (this.streaming) {
          void this.streamLoop();
        }
      }
    }
  }

  refresh(): Promise<boolean> {
    if (this.closed) {
      return Promise.resolve(false);
    }
    this.refreshPromise ??= this.fetchSnapshot().finally(() => {
      this.refreshPromise = null;
    });
    return this.refreshPromise;
  }

  close(): void {
    if (this.closed) {
      return;
    }
    this.closed = true;
    if (this.pollTimer !== null) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
      this.reconnectResolver?.();
      this.reconnectResolver = null;
    }
    this.streamAbort?.abort();
    this.streamAbort = null;
    if (this.analyticsTimer !== null) {
      clearTimeout(this.analyticsTimer);
      this.analyticsTimer = null;
    }
    this.analyticsAbort?.abort();
    this.analyticsAbort = null;
    this.analyticsDropped += this.analyticsQueue.length;
    this.analyticsQueue.length = 0;
    this.listeners.clear();
  }

  setContext(context: EvaluationContext): void {
    if (this.context === context) {
      return;
    }
    this.context = context;
    this.activateChange();
  }

  getContext(): EvaluationContext {
    return this.context;
  }

  getSnapshotRevision(): number | null {
    return this.snapshot?.revision ?? null;
  }

  getVersion = (): number => this.version;

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  boolVariation(flagKey: string, defaultValue: boolean): boolean {
    return this.boolVariationDetail(flagKey, defaultValue).value;
  }

  boolVariationDetail(flagKey: string, defaultValue: boolean): EvaluationDetail<boolean> {
    const detail = evaluateBoolean(this.snapshot, flagKey, this.context, defaultValue);
    this.recordAnalytics(flagKey, detail);
    return detail;
  }

  stringVariation(flagKey: string, defaultValue: string): string {
    return this.stringVariationDetail(flagKey, defaultValue).value;
  }

  stringVariationDetail(flagKey: string, defaultValue: string): EvaluationDetail<string> {
    const detail = evaluateString(this.snapshot, flagKey, this.context, defaultValue);
    this.recordAnalytics(flagKey, detail);
    return detail;
  }

  numberVariation(flagKey: string, defaultValue: number): number {
    return this.numberVariationDetail(flagKey, defaultValue).value;
  }

  numberVariationDetail(flagKey: string, defaultValue: number): EvaluationDetail<number> {
    const detail = evaluateNumber(this.snapshot, flagKey, this.context, defaultValue);
    this.recordAnalytics(flagKey, detail);
    return detail;
  }

  jsonVariation<T extends JsonValue>(flagKey: string, defaultValue: T): JsonValue | T {
    return this.jsonVariationDetail(flagKey, defaultValue).value;
  }

  jsonVariationDetail<T extends JsonValue>(
    flagKey: string,
    defaultValue: T,
  ): EvaluationDetail<JsonValue | T> {
    const detail = evaluateJson(this.snapshot, flagKey, this.context, defaultValue);
    this.recordAnalytics(flagKey, detail);
    return detail;
  }

  flushAnalytics(): Promise<void> {
    if (!this.analyticsEnabled || this.closed || this.analyticsQueue.length === 0) {
      return Promise.resolve();
    }
    this.analyticsFlush ??= this.sendAnalyticsBatch().finally(() => {
      this.analyticsFlush = null;
      if (!this.closed && this.analyticsQueue.length > 0) this.scheduleAnalyticsFlush();
    });
    return this.analyticsFlush;
  }

  getAnalyticsStatistics(): BrowserAnalyticsStatistics {
    return {
      queued: this.analyticsQueued,
      sent: this.analyticsSent,
      dropped: this.analyticsDropped,
      failedBatches: this.analyticsFailedBatches,
    };
  }

  private async fetchSnapshot(): Promise<boolean> {
    const abort = new AbortController();
    const timeout = setTimeout(() => abort.abort(), this.bootstrapTimeoutMs);
    try {
      const headers = new Headers({ Accept: 'application/json' });
      if (this.etag !== null) {
        headers.set('If-None-Match', this.etag);
      }
      const response = await this.fetcher(this.snapshotUrl(), {
        method: 'GET',
        headers,
        credentials: 'omit',
        redirect: 'error',
        signal: abort.signal,
      });
      if (response.status === 304) {
        return false;
      }
      if (response.status !== 200) {
        throw new Error(`Snapshot request failed with status ${response.status}`);
      }
      const contentLength = Number(response.headers.get('content-length'));
      if (Number.isFinite(contentLength) && contentLength > this.maximumSnapshotBytes) {
        throw new Error('Snapshot response exceeds the configured byte limit');
      }
      const json = await response.text();
      if (new TextEncoder().encode(json).length > this.maximumSnapshotBytes) {
        throw new Error('Snapshot response exceeds the configured byte limit');
      }
      const candidate = parseSnapshot(json);
      requireMatchingHeader(response, 'X-LaunchForge-Revision', String(candidate.revision));
      requireMatchingHeader(response, 'X-LaunchForge-Checksum', candidate.checksum);
      requireMatchingHeader(
        response,
        'X-LaunchForge-Schema-Version',
        String(candidate.schemaVersion),
      );
      if (this.snapshot !== null && candidate.revision < this.snapshot.revision) {
        return false;
      }
      if (
        this.snapshot !== null &&
        candidate.revision === this.snapshot.revision &&
        candidate.checksum !== this.snapshot.checksum
      ) {
        throw new Error('Snapshot reused a revision with different content');
      }
      if (this.snapshot?.checksum === candidate.checksum) {
        this.etag = response.headers.get('etag') ?? this.etag;
        return false;
      }
      this.snapshot = candidate;
      this.etag = response.headers.get('etag');
      this.activateChange();
      return true;
    } finally {
      clearTimeout(timeout);
    }
  }

  private schedulePoll(): void {
    if (this.closed) {
      return;
    }
    const jitter = 0.8 + this.random() * 0.4;
    this.pollTimer = setTimeout(
      () => {
        this.pollTimer = null;
        void this.refresh()
          .catch(() => undefined)
          .finally(() => this.schedulePoll());
      },
      Math.round(this.pollIntervalMs * jitter),
    );
  }

  private async streamLoop(): Promise<void> {
    let failures = 0;
    while (!this.closed) {
      try {
        await this.consumeStream();
        failures = 0;
      } catch (error) {
        if (this.closed || (error instanceof DOMException && error.name === 'AbortError')) {
          return;
        }
        failures = Math.min(failures + 1, 6);
      }
      if (!this.closed) {
        const base = Math.min(30_000, 1000 * 2 ** failures);
        await this.waitForReconnect(Math.round(base * (0.8 + this.random() * 0.4)));
      }
    }
  }

  private async consumeStream(): Promise<void> {
    const abort = new AbortController();
    this.streamAbort = abort;
    const headers = new Headers({ Accept: 'text/event-stream' });
    const revision = this.getSnapshotRevision();
    if (revision !== null) {
      headers.set('Last-Event-ID', String(revision));
    }
    try {
      const response = await this.fetcher(this.streamUrl(), {
        method: 'GET',
        headers,
        credentials: 'omit',
        redirect: 'error',
        signal: abort.signal,
      });
      if (response.status !== 200 || response.body === null) {
        throw new Error(`Stream request failed with status ${response.status}`);
      }
      await consumeRevisionStream(response.body, async (revisionHint) => {
        if ((this.getSnapshotRevision() ?? 0) < revisionHint) {
          await this.refresh();
        }
      });
    } finally {
      if (this.streamAbort === abort) {
        this.streamAbort = null;
      }
    }
  }

  private waitForReconnect(milliseconds: number): Promise<void> {
    return new Promise((resolve) => {
      this.reconnectResolver = resolve;
      this.reconnectTimer = setTimeout(() => {
        this.reconnectTimer = null;
        this.reconnectResolver = null;
        resolve();
      }, milliseconds);
    });
  }

  private activateChange(): void {
    this.version += 1;
    for (const listener of this.listeners) {
      listener();
    }
  }

  private recordAnalytics(flagKey: string, detail: EvaluationDetail<unknown>): void {
    if (!this.analyticsEnabled || this.closed || detail.snapshotRevision === undefined) return;
    if (this.analyticsQueue.length >= this.analyticsQueueCapacity) {
      this.analyticsDropped += 1;
      return;
    }
    this.analyticsQueue.push({
      eventId: crypto.randomUUID(),
      occurredAt: new Date().toISOString(),
      flagKey,
      variationId: detail.variationId ?? null,
      reason: detail.reason,
      revision: detail.snapshotRevision,
    });
    this.analyticsQueued += 1;
    if (this.analyticsQueue.length >= this.analyticsBatchSize) {
      void this.flushAnalytics();
    } else {
      this.scheduleAnalyticsFlush();
    }
  }

  private scheduleAnalyticsFlush(): void {
    if (this.analyticsTimer !== null || this.closed) return;
    this.analyticsTimer = setTimeout(() => {
      this.analyticsTimer = null;
      void this.flushAnalytics();
    }, this.analyticsFlushIntervalMs);
  }

  private async sendAnalyticsBatch(): Promise<void> {
    if (this.analyticsTimer !== null) {
      clearTimeout(this.analyticsTimer);
      this.analyticsTimer = null;
    }
    const events = this.analyticsQueue.splice(0, this.analyticsBatchSize);
    const abort = new AbortController();
    this.analyticsAbort = abort;
    const timeout = setTimeout(() => abort.abort(), this.analyticsRequestTimeoutMs);
    try {
      const response = await this.fetcher(this.analyticsUrl(), {
        method: 'POST',
        headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
        credentials: 'omit',
        redirect: 'error',
        signal: abort.signal,
        body: JSON.stringify({
          eventType: 'analytics.evaluation-batch.v1',
          schemaVersion: 1,
          events,
        }),
      });
      if (response.status >= 200 && response.status < 300) {
        this.analyticsSent += events.length;
      } else {
        this.analyticsFailedBatches += 1;
        this.analyticsDropped += events.length;
      }
    } catch {
      this.analyticsFailedBatches += 1;
      this.analyticsDropped += events.length;
    } finally {
      clearTimeout(timeout);
      if (this.analyticsAbort === abort) {
        this.analyticsAbort = null;
      }
    }
  }

  private snapshotUrl(): string {
    return `${this.baseUrl}/sdk/v1/client/${this.clientKey}/snapshot`;
  }

  private streamUrl(): string {
    return `${this.baseUrl}/sdk/v1/client/${this.clientKey}/stream`;
  }

  private analyticsUrl(): string {
    return `${this.baseUrl}/events/v1/client/${this.clientKey}/evaluations/batch`;
  }
}

interface AnalyticsEvent {
  readonly eventId: string;
  readonly occurredAt: string;
  readonly flagKey: string;
  readonly variationId: string | null;
  readonly reason: string;
  readonly revision: number;
}

export function defaultEvaluationContext(key: string): EvaluationContext {
  return createEvaluationContext(key);
}

async function consumeRevisionStream(
  stream: ReadableStream<Uint8Array>,
  onRevision: (revision: number) => Promise<void>,
): Promise<void> {
  const reader = stream.getReader();
  const decoder = new TextDecoder('utf-8', { fatal: true });
  let buffer = '';
  while (true) {
    const result = await reader.read();
    if (result.done) {
      buffer += decoder.decode();
      if (buffer.trim().length > 0) {
        await dispatchSse(buffer, onRevision);
      }
      return;
    }
    buffer += decoder.decode(result.value, { stream: true }).replaceAll('\r\n', '\n');
    if (buffer.length > 16 * 1024) {
      throw new Error('SSE event buffer exceeds 16 KiB');
    }
    let boundary = buffer.indexOf('\n\n');
    while (boundary >= 0) {
      const event = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      await dispatchSse(event, onRevision);
      boundary = buffer.indexOf('\n\n');
    }
  }
}

async function dispatchSse(
  eventText: string,
  onRevision: (revision: number) => Promise<void>,
): Promise<void> {
  let event = 'message';
  const data: string[] = [];
  for (const line of eventText.split('\n')) {
    if (line.startsWith(':')) {
      continue;
    }
    const separator = line.indexOf(':');
    const field = separator < 0 ? line : line.slice(0, separator);
    const value = separator < 0 ? '' : line.slice(separator + 1).replace(/^ /u, '');
    if (field === 'event') {
      event = value;
    } else if (field === 'data') {
      data.push(value);
    }
  }
  if (event !== 'revision' || data.length === 0) {
    return;
  }
  const parsed: unknown = JSON.parse(data.join('\n'));
  if (
    parsed === null ||
    typeof parsed !== 'object' ||
    !Object.hasOwn(parsed, 'revision') ||
    !Number.isSafeInteger((parsed as { readonly revision?: unknown }).revision) ||
    ((parsed as { readonly revision: number }).revision ?? 0) <= 0
  ) {
    throw new Error('Revision SSE data is invalid');
  }
  await onRevision((parsed as { readonly revision: number }).revision);
}

function requireBaseUrl(value: string): string {
  const url = new URL(value);
  if (
    !['http:', 'https:'].includes(url.protocol) ||
    url.username ||
    url.password ||
    url.search ||
    url.hash
  ) {
    throw new Error('baseUrl must be an HTTP(S) origin or path base without credentials');
  }
  return value.replace(/\/+$/u, '');
}

function boundedInteger(value: number, minimum: number, maximum: number, label: string): number {
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    throw new Error(`${label} must be between ${minimum} and ${maximum}`);
  }
  return value;
}

function requireMatchingHeader(response: Response, name: string, expected: string): void {
  const value = response.headers.get(name);
  if (value !== null && value !== expected) {
    throw new Error(`${name} does not match the snapshot body`);
  }
}
